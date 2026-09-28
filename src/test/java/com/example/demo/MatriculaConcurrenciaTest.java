package com.example.demo;

import com.example.demo.dto.MatriculaRequest;
import com.example.demo.dto.MatriculaResponse;
import com.example.demo.entity.*;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.*;
import com.example.demo.service.MatriculaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueba el BLOQUEO PESIMISTA lanzando muchas matriculas EXACTAMENTE a la vez.
 * Sin el bloqueo, estas pruebas fallan (sobrecupo o doble matricula).
 */
@SpringBootTest
class MatriculaConcurrenciaTest {

    @Autowired MatriculaService matriculaService;
    @Autowired MatriculaRepository matriculaRepository;
    @Autowired SeccionRepository seccionRepository;
    @Autowired AlumnoRepository alumnoRepository;
    @Autowired GradoRepository gradoRepository;
    @Autowired AnioEscolarRepository anioRepository;

    private static final AtomicInteger DNI = new AtomicInteger(60000000);

    @Test
    void diezSecretariasPeleanTresVacantes_soloTresGanan() throws Exception {

        Seccion seccion = crearSeccion("X", 3);
        List<Alumno> alumnos = crearAlumnos(10);

        Resultado r = enParalelo(10, i ->
                () -> matriculaService.matricular(
                        new MatriculaRequest(alumnos.get(i).getId(), seccion.getId()), "test"));

        assertEquals(3, r.exitos.get(), "Solo deben entrar 3 alumnos");
        assertEquals(7, r.sinVacante.get(), "Los otros 7 deben ser rechazados");
        assertEquals(0, vacantes(seccion));
        assertEquals(3, matriculaRepository.countBySeccionIdAndEstado(seccion.getId(), EstadoMatricula.ACTIVA));
    }

    @Test
    void mismoAlumnoEnDosSeccionesALaVez_soloUnaMatricula() throws Exception {

        Seccion s1 = crearSeccion("Y", 5);
        Seccion s2 = crearSeccion("W", 5);
        Alumno alumno = crearAlumnos(1).getFirst();
        List<Seccion> destinos = List.of(s1, s2, s1, s2, s1, s2);

        Resultado r = enParalelo(destinos.size(), i ->
                () -> matriculaService.matricular(
                        new MatriculaRequest(alumno.getId(), destinos.get(i).getId()), "test"));

        assertEquals(1, r.exitos.get(), "Un alumno solo puede tener una matricula por anio");
        assertEquals(9, vacantes(s1) + vacantes(s2), "Solo se debe consumir una vacante en total");
    }

    @Test
    void trasladosCruzados_noProducenDeadlock() throws Exception {

        Seccion a = crearSeccion("Q", 10);
        Seccion b = crearSeccion("R", 10);
        List<Alumno> alumnos = crearAlumnos(6);

        // 3 alumnos en A y 3 en B
        List<MatriculaResponse> matriculas = new ArrayList<>();
        for (int i = 0; i < alumnos.size(); i++) {
            Seccion s = i % 2 == 0 ? a : b;
            matriculas.add(matriculaService.matricular(new MatriculaRequest(alumnos.get(i).getId(), s.getId()), "test"));
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
