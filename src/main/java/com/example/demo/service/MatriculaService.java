package com.example.demo.service;

import com.example.demo.dto.MatriculaRequest;
import com.example.demo.dto.MatriculaResponse;
import com.example.demo.dto.VacanteMensaje;
import com.example.demo.entity.*;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.AlumnoRepository;
import com.example.demo.repository.MatriculaRepository;
import com.example.demo.repository.SeccionRepository;
import com.example.demo.websocket.VacantesCambiadasEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                            ApplicationEventPublisher eventos,
                            @Value("${app.demo.retardo-matricula-ms:0}") long retardoDemoMs) {
        this.matriculaRepository = matriculaRepository;
        this.alumnoRepository = alumnoRepository;
        this.seccionRepository = seccionRepository;
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
    // ANULAR
    // =========================================================
    @Transactional
    public MatriculaResponse anular(Integer matriculaId, String usuario) {

        // Bloquea la matricula
        Matricula matricula = bloquearMatricula(matriculaId);

        if (matricula.estaAnulada()) {
            throw new NegocioException("La matricula ya estaba anulada");
        }

        Seccion seccion = seccionRepository.findByIdParaActualizar(matricula.getSeccion().getId())
                .orElseThrow(() -> new NegocioException("La seccion no existe"));

        validarAnioAbierto(seccion);
        pausaDemo();

        // Si estaba formalizada (MATRICULADA), devuelve la vacante que habia ocupado
        if (matricula.getEstado() == EstadoMatricula.MATRICULADA) {
            seccion.liberarVacante();
        }
        matricula.setEstado(EstadoMatricula.ANULADA);

        avisar(List.of(seccion), usuario + " anulo el registro de "
                + matricula.getAlumno().getNombreCompleto() + " (" + seccion.getDescripcion() + ")");

        return MatriculaResponse.desde(matricula);
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
