package com.example.demo.service;

import com.example.demo.dto.AnularMatriculaRequest;
import com.example.demo.dto.MatriculaRequest;
import com.example.demo.dto.MatriculaResponse;
import com.example.demo.dto.ReciboResponse;
import com.example.demo.dto.VacanteMensaje;
import com.example.demo.entity.*;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.AlumnoRepository;
import com.example.demo.repository.CuotaRepository;
import com.example.demo.repository.MatriculaRepository;
import com.example.demo.repository.ReciboRepository;
import com.example.demo.repository.SeccionRepository;
import com.example.demo.websocket.VacantesCambiadasEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Matricula, anulacion y traslado con BLOQUEO PESIMISTA.
 *
 * Orden fijo de bloqueo en TODAS las operaciones (evita deadlocks):
 *     1) matricula   2) alumno   3) secciones (de menor a mayor id)
 *
 * Los bloqueos se liberan solos al terminar el metodo (commit o rollback).
 */
@Service
public class MatriculaService {

    private final MatriculaRepository matriculaRepository;
    private final AlumnoRepository alumnoRepository;
    private final SeccionRepository seccionRepository;
    private final CuotaRepository cuotaRepository;
    private final ReciboRepository reciboRepository;
    private final PagoService pagoService;
    private final ApplicationEventPublisher eventos;

    /**
     * Pausa artificial MIENTRAS se tiene el bloqueo. Solo para la exposicion:
     * con 5000 ms se ve como la segunda pestana "espera" a la primera.
     * En produccion vale 0.
     */
    private final long retardoDemoMs;

    public MatriculaService(MatriculaRepository matriculaRepository,
                            AlumnoRepository alumnoRepository,
                            SeccionRepository seccionRepository,
                            CuotaRepository cuotaRepository,
                            ReciboRepository reciboRepository,
                            PagoService pagoService,
                            ApplicationEventPublisher eventos,
                            @Value("${app.demo.retardo-matricula-ms:0}") long retardoDemoMs) {
        this.matriculaRepository = matriculaRepository;
        this.alumnoRepository = alumnoRepository;
        this.seccionRepository = seccionRepository;
        this.cuotaRepository = cuotaRepository;
        this.reciboRepository = reciboRepository;
        this.pagoService = pagoService;
        this.eventos = eventos;
        this.retardoDemoMs = retardoDemoMs;
    }

    @Transactional(readOnly = true)
    public List<MatriculaResponse> listar(Integer anioId, Integer seccionId,
                                          EstadoMatricula estado, String texto) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        return matriculaRepository.filtrar(anioId, seccionId, estado, filtro)
                .stream()
                .map(MatriculaResponse::desde)
                .toList();
    }

    /** Nomina (alumnos con matricula formalizada y pagada) de una seccion. */
    @Transactional(readOnly = true)
    public List<MatriculaResponse> nomina(Integer seccionId) {
        return matriculaRepository
                .findBySeccionIdAndEstadoOrderByAlumnoApellidosAscAlumnoNombresAsc(
                        seccionId, EstadoMatricula.MATRICULADA)
                .stream()
                .map(MatriculaResponse::desde)
                .toList();
    }

    // =========================================================
    // INSCRIBIR / MATRICULAR
    // =========================================================
    @Transactional
    public MatriculaResponse matricular(MatriculaRequest request, String usuario) {
        return inscribir(request, usuario);
    }

    @Transactional
    public MatriculaResponse inscribir(MatriculaRequest request, String usuario) {

        // 1) Bloquea al alumno: nadie mas puede inscribirlo en paralelo.
        Alumno alumno = alumnoRepository.findByIdParaMatricula(request.alumnoId())
                .orElseThrow(() -> new NegocioException("El alumno no existe"));

        // 2) Obtiene la seccion
        Seccion seccion = seccionRepository.findById(request.seccionId())
                .orElseThrow(() -> new NegocioException("La seccion no existe"));

        validarAnioAbierto(seccion);

        AnioEscolar anio = seccion.getAnioEscolar();
        if (matriculaRepository.existsByAlumnoIdAndSeccionAnioEscolarIdAndEstadoIn(
                alumno.getId(), anio.getId(), List.of(EstadoMatricula.INSCRITA, EstadoMatricula.MATRICULADA))) {
            throw new NegocioException(alumno.getNombreCompleto()
                    + " ya tiene un registro de matricula o inscripcion en el anio " + anio.getAnio());
        }

        // Verificacion preventiva de vacantes disponibles
        if (seccion.getVacantesDisponibles() <= 0) {
            throw new NegocioException("La seccion " + seccion.getDescripcion() + " ya no cuenta con vacantes disponibles");
        }

        pausaDemo();

        // 3) La inscripcion NO descuenta vacante todavia.
        // La vacante se ocupara con bloqueo pesimista al confirmar el pago del Derecho de Matricula.
        Matricula matricula = matriculaRepository.save(new Matricula(alumno, seccion, usuario, EstadoMatricula.INSCRITA));

        avisar(List.of(seccion), usuario + " inscribio a " + alumno.getNombreCompleto()
                + " en " + seccion.getDescripcion() + " (pendiente de pago de matricula)");

        return MatriculaResponse.desde(matricula);
    }

    // =========================================================
    // ANULAR (Con soporte de anulacion de deuda y boletas DEV-...)
    // =========================================================
    @Transactional
    public MatriculaResponse anular(Integer matriculaId, String usuario) {
        return anular(matriculaId, null, usuario, true);
    }

    @Transactional
    public MatriculaResponse anular(Integer matriculaId, AnularMatriculaRequest request, Usuario usuarioActual) {
        boolean esAdmin = usuarioActual != null && usuarioActual.esAdmin();
        String username = usuarioActual != null ? usuarioActual.getUsername() : "sistema";
        return anular(matriculaId, request, username, esAdmin);
    }

    @Transactional
    public MatriculaResponse anular(Integer matriculaId, AnularMatriculaRequest request, String usuario, boolean esAdmin) {

        // 1) Bloquea la matricula
        Matricula matricula = bloquearMatricula(matriculaId);

        if (matricula.estaAnulada()) {
            throw new NegocioException("La matricula ya estaba anulada");
        }

        boolean eraMatriculada = matricula.getEstado() == EstadoMatricula.MATRICULADA;
        if (eraMatriculada && !esAdmin) {
            throw new NegocioException("Solo un usuario con rol ADMIN puede autorizar la anulacion de una matricula con pagos registrados");
        }

        String motivo;
        if (request != null && request.motivo() != null && !request.motivo().isBlank()) {
            motivo = request.motivo().trim();
        } else if (eraMatriculada && request != null) {
            throw new NegocioException("El motivo de anulacion es obligatorio");
        } else {
            motivo = "Anulacion de registro";
        }

        String metodoDevolucion = (request != null && request.metodoDevolucion() != null && !request.metodoDevolucion().isBlank())
                ? request.metodoDevolucion().trim().toUpperCase()
                : "EFECTIVO";

        Seccion seccion = seccionRepository.findByIdParaActualizar(matricula.getSeccion().getId())
                .orElseThrow(() -> new NegocioException("La seccion no existe"));

        validarAnioAbierto(seccion);
        pausaDemo();

        LocalDateTime ahora = LocalDateTime.now();
        List<ReciboResponse> boletasDevolucion = new ArrayList<>();

        // Si estaba formalizada (MATRICULADA):
        // - Las cuotas pendientes (DEBE) pasan a ANULADA (deuda queda en S/ 0.00)
        // - Los recibos seleccionados pasan a DEVUELTO y emiten 1 a 1 su Boleta de Devolucion (DEV-00000X)
        // - Se libera la vacante ocupada en la seccion
        if (eraMatriculada) {
            List<Cuota> cuotas = cuotaRepository.findByMatriculaIdOrderByNumeroCuotaAsc(matriculaId);
            for (Cuota c : cuotas) {
                if (c.estaDebe()) {
                    c.setEstado(EstadoCuota.ANULADA);
                }
            }

            if (request != null && request.recibosADevolverIds() != null && !request.recibosADevolverIds().isEmpty()) {
                Set<Integer> idsUnicos = new LinkedHashSet<>(request.recibosADevolverIds());
                for (Integer reciboId : idsUnicos) {
                    if (reciboId == null) continue;

                    Recibo reciboOriginal = reciboRepository.findById(reciboId)
                            .orElseThrow(() -> new NegocioException("No existe el recibo " + reciboId));

                    if (!reciboOriginal.getMatricula().getId().equals(matriculaId)) {
                        throw new NegocioException("El recibo " + reciboOriginal.getNumero() + " no pertenece a esta matricula");
                    }
                    if (reciboOriginal.getTipo() != TipoRecibo.INGRESO) {
                        throw new NegocioException("Solo se pueden reembolsar recibos de ingreso (REC-...)");
                    }
                    if (reciboOriginal.getEstado() == EstadoRecibo.DEVUELTO) {
                        throw new NegocioException("El recibo " + reciboOriginal.getNumero() + " ya fue devuelto previamente");
                    }

                    // 4a. Marca Recibo original como DEVUELTO
                    reciboOriginal.setEstado(EstadoRecibo.DEVUELTO);
                    reciboOriginal.setMotivoAnulacion(motivo);
                    reciboOriginal.setFechaAnulacion(ahora);
                    reciboOriginal.setAnuladoPor(usuario);
                    reciboRepository.save(reciboOriginal);

                    // 4b. Si pertenece a una Cuota, pasa la Cuota a DEVUELTA
                    for (Cuota c : cuotas) {
                        if (c.getRecibo() != null && c.getRecibo().getId().equals(reciboOriginal.getId())) {
                            c.setEstado(EstadoCuota.DEVUELTA);
                            break;
                        }
                    }

                    // 4c. Crea y guarda la nueva Boleta de Devolucion individual (DEV-00000X)
                    String numeroDev = pagoService.generarNumeroDevolucion();
                    Recibo boletaDev = Recibo.crearDevolucion(numeroDev, reciboOriginal, motivo, usuario, metodoDevolucion);
                    boletaDev = reciboRepository.save(boletaDev);
                    reciboOriginal.setBoletaDevolucion(boletaDev);

                    boletasDevolucion.add(ReciboResponse.desde(boletaDev));
                }
            }

            cuotaRepository.saveAll(cuotas);
            seccion.liberarVacante();
        }

        matricula.setEstado(EstadoMatricula.ANULADA);
        matricula.setFechaAnulacion(ahora);
        matricula.setAnuladoPor(usuario);
        matricula.setMotivoAnulacion(motivo);
        matriculaRepository.save(matricula);

        String detalleDev = boletasDevolucion.isEmpty()
                ? ""
                : " · " + boletasDevolucion.size() + " boleta(s) de devolucion emitida(s)";

        avisar(List.of(seccion), usuario + " anulo el registro de "
                + matricula.getAlumno().getNombreCompleto() + " (" + seccion.getDescripcion() + ")" + detalleDev);

        return MatriculaResponse.desde(matricula, boletasDevolucion);
    }

    // =========================================================
    // TRASLADO DE SECCION (mismo grado, mismo anio)
    // =========================================================
    @Transactional
    public MatriculaResponse trasladar(Integer matriculaId, Integer destinoId, String usuario) {

        Matricula matricula = bloquearMatricula(matriculaId);

        if (matricula.estaAnulada()) {
            throw new NegocioException("No se puede trasladar una matricula anulada");
        }

        Integer origenId = matricula.getSeccion().getId();
        if (origenId.equals(destinoId)) {
            throw new NegocioException("El alumno ya esta en esa seccion");
        }

        /*
         * Se bloquean DOS secciones. Siempre en orden de id (menor primero).
         * Sin este orden:  T1 traslada A->B (bloquea A, espera B)
         *                  T2 traslada B->A (bloquea B, espera A)  => DEADLOCK
         */
        Seccion primera = bloquearSeccion(Math.min(origenId, destinoId));
        Seccion segunda = bloquearSeccion(Math.max(origenId, destinoId));
        Seccion origen = primera.getId().equals(origenId) ? primera : segunda;
        Seccion destino = origen == primera ? segunda : primera;

        if (!destino.getAnioEscolar().getId().equals(origen.getAnioEscolar().getId())
                || !destino.getGrado().getId().equals(origen.getGrado().getId())) {
            throw new NegocioException("El traslado solo es entre secciones del mismo grado y anio");
        }
        validarAnioAbierto(destino);
        pausaDemo();

        if (matricula.getEstado() == EstadoMatricula.MATRICULADA) {
            destino.ocuparVacante();     // si no hay vacante, falla y NO se toca el origen (rollback)
            origen.liberarVacante();
        } else {
            if (destino.getVacantesDisponibles() <= 0) {
                throw new NegocioException("La seccion destino " + destino.getDescripcion() + " no tiene vacantes disponibles");
            }
        }
        matricula.setSeccion(destino);

        avisar(List.of(origen, destino), usuario + " traslado a "
                + matricula.getAlumno().getNombreCompleto() + " de "
                + origen.getDescripcion() + " a " + destino.getDescripcion());

        return MatriculaResponse.desde(matricula);
    }

    // =========================================================
    // Apoyo
    // =========================================================

    private Matricula bloquearMatricula(Integer id) {
        return matriculaRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> new NegocioException("No existe la matricula " + id));
    }

    private Seccion bloquearSeccion(Integer id) {
        return seccionRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> new NegocioException("No existe la seccion " + id));
    }

    private void validarAnioAbierto(Seccion seccion) {
        if (!seccion.getAnioEscolar().estaAbierto()) {
            throw new NegocioException("El anio escolar " + seccion.getAnioEscolar().getAnio()
                    + " esta cerrado: no admite cambios de matricula");
        }
    }

    /** Se publica ahora, pero NotificadorWebSocket solo lo envia tras el COMMIT. */
    private void avisar(List<Seccion> secciones, String actividad) {
        eventos.publishEvent(new VacantesCambiadasEvent(
                secciones.stream().map(VacanteMensaje::desde).toList(), actividad));
    }

    private void pausaDemo() {
        if (retardoDemoMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retardoDemoMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
