package com.example.demo.service;

import com.example.demo.dto.SeccionRequest;
import com.example.demo.dto.SeccionResponse;
import com.example.demo.dto.VacanteMensaje;
import com.example.demo.entity.AnioEscolar;
import com.example.demo.entity.Docente;
import com.example.demo.entity.Grado;
import com.example.demo.entity.Seccion;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.*;
import com.example.demo.websocket.VacantesCambiadasEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeccionService {

    private final SeccionRepository seccionRepository;
    private final AnioEscolarRepository anioRepository;
    private final GradoRepository gradoRepository;
    private final DocenteRepository docenteRepository;
    private final MatriculaRepository matriculaRepository;
    private final ApplicationEventPublisher eventos;

    public SeccionService(SeccionRepository seccionRepository,
                          AnioEscolarRepository anioRepository,
                          GradoRepository gradoRepository,
                          DocenteRepository docenteRepository,
                          MatriculaRepository matriculaRepository,
                          ApplicationEventPublisher eventos) {
        this.seccionRepository = seccionRepository;
        this.anioRepository = anioRepository;
        this.gradoRepository = gradoRepository;
        this.docenteRepository = docenteRepository;
        this.matriculaRepository = matriculaRepository;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<SeccionResponse> listar(Integer anioId, Integer gradoId) {
        return seccionRepository.filtrar(anioId, gradoId)
                .stream()
                .map(SeccionResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeccionResponse obtenerPorId(Integer id) {
        return SeccionResponse.desde(seccionRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe la seccion " + id)));
    }

    @Transactional
    public SeccionResponse crear(SeccionRequest request) {

        String letra = request.letra().trim().toUpperCase();

        if (seccionRepository.existsByAnioEscolarIdAndGradoIdAndLetra(
                request.anioEscolarId(), request.gradoId(), letra)) {
            throw new NegocioException("Esa seccion ya existe para ese grado y anio");
        }

        Seccion seccion = new Seccion(
                buscarAnio(request.anioEscolarId()),
                buscarGrado(request.gradoId()),
                letra,
                request.turno(),
                limpiar(request.aula()),
                buscarTutor(request.tutorId()),
                request.vacantesMaximas()
        );

        return SeccionResponse.desde(seccionRepository.save(seccion));
    }

    /**
     * Editar las vacantes compite con las matriculas en curso,
     * por eso tambien se hace con la fila BLOQUEADA.
     */
    @Transactional
    public SeccionResponse actualizar(Integer id, SeccionRequest request) {

        Seccion seccion = seccionRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> new NegocioException("No existe la seccion " + id));

        String letra = request.letra().trim().toUpperCase();
        boolean cambiaUbicacion = !seccion.getAnioEscolar().getId().equals(request.anioEscolarId())
                || !seccion.getGrado().getId().equals(request.gradoId())
                || !seccion.getLetra().equals(letra);

        if (cambiaUbicacion) {
            if (seccion.getMatriculados() > 0) {
                throw new NegocioException("La seccion tiene alumnos matriculados: "
                        + "no se puede cambiar su anio, grado ni letra");
            }
            if (seccionRepository.existsByAnioEscolarIdAndGradoIdAndLetra(
                    request.anioEscolarId(), request.gradoId(), letra)) {
                throw new NegocioException("Esa seccion ya existe para ese grado y anio");
            }
            seccion.setAnioEscolar(buscarAnio(request.anioEscolarId()));
            seccion.setGrado(buscarGrado(request.gradoId()));
            seccion.setLetra(letra);
        }

        seccion.setTurno(request.turno());
        seccion.setAula(limpiar(request.aula()));
        seccion.setTutor(buscarTutor(request.tutorId()));
        seccion.cambiarVacantesMaximas(request.vacantesMaximas());

        eventos.publishEvent(new VacantesCambiadasEvent(
                List.of(VacanteMensaje.desde(seccion)), null));

        return SeccionResponse.desde(seccion);
    }

    @Transactional
    public void eliminar(Integer id) {
        if (matriculaRepository.existsBySeccionId(id)) {
            throw new NegocioException("No se puede eliminar: la seccion tiene matriculas registradas");
        }
        seccionRepository.delete(seccionRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe la seccion " + id)));
    }

    private AnioEscolar buscarAnio(Integer id) {
        return anioRepository.findById(id)
                .orElseThrow(() -> new NegocioException("El anio escolar no existe"));
    }

    private Grado buscarGrado(Integer id) {
        return gradoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("El grado no existe"));
    }

    private Docente buscarTutor(Integer id) {
        if (id == null) {
            return null;
        }
        return docenteRepository.findById(id)
                .orElseThrow(() -> new NegocioException("El docente tutor no existe"));
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
