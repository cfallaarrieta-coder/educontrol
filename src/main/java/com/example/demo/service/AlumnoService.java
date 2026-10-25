package com.example.demo.service;

import com.example.demo.dto.AlumnoRequest;
import com.example.demo.dto.AlumnoResponse;
import com.example.demo.entity.*;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.AlumnoRepository;
import com.example.demo.repository.AnioEscolarRepository;
import com.example.demo.repository.MatriculaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlumnoService {

    private final AlumnoRepository alumnoRepository;
    private final MatriculaRepository matriculaRepository;
    private final AnioEscolarRepository anioEscolarRepository;

    public AlumnoService(AlumnoRepository alumnoRepository,
                         MatriculaRepository matriculaRepository,
                         AnioEscolarRepository anioEscolarRepository) {
        this.alumnoRepository = alumnoRepository;
        this.matriculaRepository = matriculaRepository;
        this.anioEscolarRepository = anioEscolarRepository;
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponse> buscar(String texto) {
        return buscar(texto, null, null);
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponse> buscar(String texto, Integer anioId, SituacionAlumno situacion) {
        String textoLimpio = vacioANull(texto);
        Integer anioEfectivo = resolverAnioId(anioId);

        List<Alumno> alumnos;
        if (anioEfectivo != null) {
            String situacionStr = situacion != null ? situacion.name() : null;
            alumnos = alumnoRepository.filtrarPorSituacion(anioEfectivo, situacionStr, textoLimpio);
        } else {
            alumnos = (textoLimpio == null)
                    ? alumnoRepository.findAllByOrderByApellidosAscNombresAsc()
                    : alumnoRepository.buscar(textoLimpio);
        }

        return mapearConSituacion(alumnos, anioEfectivo);
    }

    @Transactional(readOnly = true)
    public AlumnoResponse obtenerPorId(Integer id) {
        return obtenerPorId(id, null);
    }

    @Transactional(readOnly = true)
    public AlumnoResponse obtenerPorId(Integer id, Integer anioId) {
        Alumno alumno = buscarEntidad(id);
        return mapearConSituacion(List.of(alumno), resolverAnioId(anioId)).get(0);
    }

    @Transactional
    public AlumnoResponse crear(AlumnoRequest request) {
        if (alumnoRepository.existsByDni(request.dni())) {
            throw new NegocioException("Ya existe un alumno con DNI " + request.dni());
        }
        Alumno alumno = new Alumno(request.dni(), request.nombres().trim(), request.apellidos().trim(),
                request.fechaNacimiento(), request.apoderado().trim(), vacioANull(request.telefonoApoderado()));
        return AlumnoResponse.desde(alumnoRepository.save(alumno));
    }

    @Transactional
    public AlumnoResponse actualizar(Integer id, AlumnoRequest request) {
        if (alumnoRepository.existsByDniAndIdNot(request.dni(), id)) {
            throw new NegocioException("Ya existe otro alumno con DNI " + request.dni());
        }
        Alumno alumno = buscarEntidad(id);
        alumno.setDni(request.dni());
        alumno.setNombres(request.nombres().trim());
        alumno.setApellidos(request.apellidos().trim());
        alumno.setFechaNacimiento(request.fechaNacimiento());
        alumno.setApoderado(request.apoderado().trim());
        alumno.setTelefonoApoderado(vacioANull(request.telefonoApoderado()));
        return obtenerPorId(alumno.getId(), null);
    }

    @Transactional
    public void eliminar(Integer id) {
        if (matriculaRepository.existsByAlumnoId(id)) {
            throw new NegocioException("No se puede eliminar: el alumno tiene matriculas registradas");
        }
        alumnoRepository.delete(buscarEntidad(id));
    }

    private Integer resolverAnioId(Integer anioId) {
        if (anioId != null) {
            return anioId;
        }
        return anioEscolarRepository.findFirstByEstadoOrderByAnioDesc(EstadoAnio.ABIERTO)
                .map(AnioEscolar::getId)
                .or(() -> anioEscolarRepository.findAllByOrderByAnioDesc().stream().findFirst().map(AnioEscolar::getId))
                .orElse(null);
    }

    private List<AlumnoResponse> mapearConSituacion(List<Alumno> alumnos, Integer anioId) {
        if (alumnos.isEmpty()) {
            return List.of();
        }
        if (anioId == null) {
            return alumnos.stream().map(AlumnoResponse::desde).toList();
        }

        List<Integer> alumnoIds = alumnos.stream().map(Alumno::getId).toList();
        List<Matricula> matriculas = matriculaRepository.findBySeccionAnioEscolarIdAndAlumnoIdIn(anioId, alumnoIds);
        Map<Integer, List<Matricula>> porAlumno = matriculas.stream()
                .collect(Collectors.groupingBy(m -> m.getAlumno().getId()));

        return alumnos.stream().map(a -> {
            List<Matricula> delAlumno = porAlumno.getOrDefault(a.getId(), List.of());

            Matricula activa = delAlumno.stream()
                    .filter(m -> m.getEstado() == EstadoMatricula.MATRICULADA
                              || m.getEstado() == EstadoMatricula.INSCRITA)
                    .findFirst()
                    .orElse(null);

            boolean tuvoAnulacion = delAlumno.stream()
                    .anyMatch(m -> m.getEstado() == EstadoMatricula.ANULADA);

            if (activa != null) {
                SituacionAlumno sit = activa.getEstado() == EstadoMatricula.MATRICULADA
                        ? SituacionAlumno.MATRICULADA
                        : SituacionAlumno.INSCRITA;
                return AlumnoResponse.desde(a, sit, activa.getId(), activa.getSeccion().getDescripcion(), false);
            }

            return AlumnoResponse.desde(a, SituacionAlumno.SIN_MATRICULA, null, null, tuvoAnulacion);
        }).toList();
    }

    private Alumno buscarEntidad(Integer id) {
        return alumnoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe un alumno con id " + id));
    }

    static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
