package com.example.demo;

import com.example.demo.dto.AnularMatriculaRequest;
import com.example.demo.dto.MatriculaRequest;
import com.example.demo.dto.MatriculaResponse;
import com.example.demo.dto.ReciboResponse;
import com.example.demo.entity.*;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.*;
import com.example.demo.service.MatriculaService;
import com.example.demo.service.PagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba el BLOQUEO PESIMISTA lanzando muchas matriculas EXACTAMENTE a la vez.
 * Sin el bloqueo, estas pruebas fallan (sobrecupo o doble matricula).
 */
@SpringBootTest
class MatriculaConcurrenciaTest {

    @Autowired PagoService pagoService;
    @Autowired MatriculaService matriculaService;
    @Autowired MatriculaRepository matriculaRepository;
    @Autowired SeccionRepository seccionRepository;
    @Autowired AlumnoRepository alumnoRepository;
    @Autowired GradoRepository gradoRepository;
    @Autowired AnioEscolarRepository anioRepository;

    private static final AtomicInteger DNI = new AtomicInteger(60000000);

    @Test
    void diezAlumnosInscritosPeleanTresVacantesAlPagarMatricula_soloTresGanan() throws Exception {

        Seccion seccion = crearSeccion("X", 3);
        List<Alumno> alumnos = crearAlumnos(10);

        List<MatriculaResponse> inscripciones = new ArrayList<>();
        for (Alumno a : alumnos) {
            inscripciones.add(matriculaService.inscribir(new MatriculaRequest(a.getId(), seccion.getId()), "test"));
        }

        // Los 10 intentan pagar el Derecho de Matricula a la vez (bloqueo pesimista sobre la seccion)
        Resultado r = enParalelo(10, i ->
                () -> pagoService.pagarMatricula(inscripciones.get(i).id(), null, "cajero"));

        assertEquals(3, r.exitos.get(), "Solo deben pagar y formalizar matricula 3 alumnos");
        assertEquals(7, r.sinVacante.get(), "Los otros 7 deben ser rechazados por falta de vacantes");
        assertEquals(0, vacantes(seccion));
        assertEquals(3, matriculaRepository.countBySeccionIdAndEstado(seccion.getId(), EstadoMatricula.MATRICULADA));
    }

    @Test
    void mismoAlumnoEnDosSeccionesALaVez_soloUnaInscripcion() throws Exception {

        Seccion s1 = crearSeccion("Y", 5);
        Seccion s2 = crearSeccion("W", 5);
        Alumno alumno = crearAlumnos(1).getFirst();
        List<Seccion> destinos = List.of(s1, s2, s1, s2, s1, s2);

        Resultado r = enParalelo(destinos.size(), i ->
                () -> matriculaService.inscribir(
                        new MatriculaRequest(alumno.getId(), destinos.get(i).getId()), "test"));

        assertEquals(1, r.exitos.get(), "Un alumno solo puede tener una inscripcion o matricula por anio");
    }

    @Test
    void trasladosCruzados_noProducenDeadlock() throws Exception {

        Seccion a = crearSeccion("Q", 10);
        Seccion b = crearSeccion("R", 10);
        List<Alumno> alumnos = crearAlumnos(6);

        // 3 alumnos en A y 3 en B, formalizados con matricula pagada
        List<MatriculaResponse> matriculas = new ArrayList<>();
        for (int i = 0; i < alumnos.size(); i++) {
            Seccion s = i % 2 == 0 ? a : b;
            MatriculaResponse ins = matriculaService.inscribir(new MatriculaRequest(alumnos.get(i).getId(), s.getId()), "test");
            pagoService.pagarMatricula(ins.id(), null, "test");
            matriculas.add(ins);
        }

        // Todos se cruzan a la vez: los de A van a B y los de B van a A
        Resultado r = enParalelo(matriculas.size(), i -> () -> {
            MatriculaResponse m = matriculas.get(i);
            Integer destino = m.seccionId().equals(a.getId()) ? b.getId() : a.getId();
            return matriculaService.trasladar(m.id(), destino, "test");
        });

        assertEquals(6, r.exitos.get(), "Con bloqueo ordenado por id, ningun traslado debe fallar");
        assertEquals(7, vacantes(a));
        assertEquals(7, vacantes(b));
    }

    @Test
    void pagoSecuencialMensualidades_respetaOrdenPrelatorio() {
        Seccion seccion = crearSeccion("Z", 10);
        Alumno alumno = crearAlumnos(1).getFirst();

        // 1) Inscribir alumno
        MatriculaResponse ins = matriculaService.inscribir(new MatriculaRequest(alumno.getId(), seccion.getId()), "test");

        // 2) Pagar Derecho de Matricula -> formaliza y crea las 10 cuotas
        pagoService.pagarMatricula(ins.id(), null, "cajero");

        var cc = pagoService.obtenerCuentaCorriente(ins.id());
        assertEquals(10, cc.cuotas().size());
        assertEquals("Marzo", cc.cuotas().get(0).mes());
        assertEquals(true, cc.cuotas().get(0).pagable());
        assertEquals(false, cc.cuotas().get(1).pagable(), "Abril no debe ser pagable si Marzo no esta pagado");

        // Intentar pagar Abril directamente debe lanzar excepcion de negocio
        assertThrows(NegocioException.class, () ->
                pagoService.pagarCuota(cc.cuotas().get(1).id(), null, "cajero"));

        // Pagar Marzo
        pagoService.pagarCuota(cc.cuotas().get(0).id(), null, "cajero");

        // Ahora Abril si debe ser pagable
        var ccDespues = pagoService.obtenerCuentaCorriente(ins.id());
        assertEquals(EstadoCuota.PAGADO, ccDespues.cuotas().get(0).estado());
        assertEquals(true, ccDespues.cuotas().get(1).pagable());

        // Pagar Abril
        pagoService.pagarCuota(ccDespues.cuotas().get(1).id(), null, "cajero");
        var ccFinal = pagoService.obtenerCuentaCorriente(ins.id());
        assertEquals(EstadoCuota.PAGADO, ccFinal.cuotas().get(1).estado());
    }

    @Test
    void anulacionConDevolucionParcialYTotal_emiteBoletasDevIndividualesYDejaDeudaCero() {
        Seccion seccion = crearSeccion("D", 5);
        Alumno alumno = crearAlumnos(1).getFirst();

        MatriculaResponse ins = matriculaService.inscribir(new MatriculaRequest(alumno.getId(), seccion.getId()), "secretaria");
        ReciboResponse recMat = pagoService.pagarMatricula(ins.id(), null, "admin");

        var ccInicial = pagoService.obtenerCuentaCorriente(ins.id());
        ReciboResponse recMarzo = pagoService.pagarCuota(ccInicial.cuotas().get(0).id(), null, "admin");
        ReciboResponse recAbril = pagoService.pagarCuota(ccInicial.cuotas().get(1).id(), null, "admin");

        // 1) SECRETARIA (esAdmin = false) NO puede anular una matricula con pagos
        assertThrows(NegocioException.class, () ->
                matriculaService.anular(ins.id(), new AnularMatriculaRequest("Intento secretaria", List.of(), "EFECTIVO"), "secretaria", false));

        // 2) ADMIN anula y devuelve solo Abril (Devolucion Parcial: Matricula y Marzo quedan EMITIDO)
        MatriculaResponse anulada = matriculaService.anular(
                ins.id(),
                new AnularMatriculaRequest("Retiro a fines de Marzo, se devuelve Abril adelantado", List.of(recAbril.id()), "TRANSFERENCIA"),
                "admin",
                true
        );

        assertEquals(EstadoMatricula.ANULADA, anulada.estado());
        assertEquals(1, anulada.boletasDevolucion().size(), "Debe haberse emitido 1 boleta DEV- por Abril");
        ReciboResponse boletaDev = anulada.boletasDevolucion().getFirst();
        assertTrue(boletaDev.numero().startsWith("DEV-"), "La boleta debe usar serie DEV-");
        assertEquals(TipoRecibo.DEVOLUCION, boletaDev.tipo());
        assertEquals(EstadoRecibo.EMITIDO, boletaDev.estado());
        assertEquals(recAbril.id(), boletaDev.reciboOrigenId());
        assertEquals(recAbril.numero(), boletaDev.reciboOrigenNumero());

        // 3) Verificar estado de cuenta tras la anulacion:
        //    - Deuda total = S/ 0.00 (Mayo..Diciembre pasaron a ANULADA)
        //    - Marzo sigue PAGADO, Abril paso a DEVUELTA
        var ccPost = pagoService.obtenerCuentaCorriente(ins.id());
        assertEquals(0, BigDecimal.ZERO.compareTo(ccPost.totalDeuda()), "La deuda pendiente debe quedar en S/ 0.00");
        assertEquals(0, recMat.monto().add(recMarzo.monto()).compareTo(ccPost.totalPagado()), "Total pagado neto = Matricula + Marzo");
        assertEquals(0, recAbril.monto().compareTo(ccPost.totalDevuelto()), "Total devuelto = Abril");
        assertEquals(EstadoCuota.PAGADO, ccPost.cuotas().get(0).estado());
        assertEquals(EstadoCuota.DEVUELTA, ccPost.cuotas().get(1).estado());
        assertEquals(boletaDev.numero(), ccPost.cuotas().get(1).boletaDevolucionNumero());
        for (int i = 2; i < 10; i++) {
            assertEquals(EstadoCuota.ANULADA, ccPost.cuotas().get(i).estado(), "Las cuotas futuras deben quedar ANULADA");
        }

        // 4) El alumno puede volver a inscribirse en el mismo anio (creando una nueva Matricula limpia)
        MatriculaResponse reInscripcion = matriculaService.inscribir(new MatriculaRequest(alumno.getId(), seccion.getId()), "secretaria");
        assertNotEquals(ins.id(), reInscripcion.id());
        assertEquals(EstadoMatricula.INSCRITA, reInscripcion.estado());
    }

    // =========================================================
    // Apoyo
    // =========================================================

    private record Resultado(AtomicInteger exitos, AtomicInteger sinVacante) {}

    /** Arranca N tareas y las suelta TODAS en el mismo instante con un CountDownLatch. */
    private Resultado enParalelo(int n, IntFunction<Callable<?>> tarea) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        Resultado r = new Resultado(new AtomicInteger(), new AtomicInteger());

        List<Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<?> c = tarea.apply(i);
            futuros.add(hilos.submit(() -> {
                largada.await();
                try {
                    c.call();
                    r.exitos.incrementAndGet();
                } catch (NegocioException e) {
                    r.sinVacante.incrementAndGet();
                }
                return null;
            }));
        }

        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);   // cualquier otro error (deadlock, timeout) hace fallar el test
        }
        hilos.shutdown();
        return r;
    }

    private Seccion crearSeccion(String letra, int vacantes) {
        AnioEscolar anio = anioRepository.findByAnio(LocalDate.now().getYear()).orElseThrow();
        Grado grado = gradoRepository.findAllByOrderByOrdenAsc().getLast();
        return seccionRepository.save(new Seccion(anio, grado, letra, Turno.MANANA, null, null, vacantes));
    }

    private List<Alumno> crearAlumnos(int n) {
        List<Alumno> lista = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lista.add(alumnoRepository.save(new Alumno(String.valueOf(DNI.incrementAndGet()),
                    "Alumno" + i, "Prueba", LocalDate.of(2012, 1, 1), "Apoderado", null)));
        }
        return lista;
    }

    private int vacantes(Seccion s) {
        return seccionRepository.findById(s.getId()).orElseThrow().getVacantesDisponibles();
    }
}
