package com.example.demo.service;

import com.example.demo.dto.AlumnoRequest;
import com.example.demo.dto.AlumnoResponse;
import com.example.demo.entity.Alumno;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.AlumnoRepository;
import com.example.demo.repository.MatriculaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlumnoService {

    private final AlumnoRepository alumnoRepository;
    private final MatriculaRepository matriculaRepository;

    public AlumnoService(AlumnoRepository alumnoRepository,
                         MatriculaRepository matriculaRepository) {
        this.alumnoRepository = alumnoRepository;
        this.matriculaRepository = matriculaRepository;
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponse> buscar(String texto) {
        List<Alumno> alumnos = (texto == null || texto.isBlank())
                ? alumnoRepository.findAllByOrderByApellidosAscNombresAsc()
                : alumnoRepository.buscar(texto.trim());
        return alumnos.stream().map(AlumnoResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public AlumnoResponse obtenerPorId(Integer id) {
        return AlumnoResponse.desde(buscarEntidad(id));
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
        return AlumnoResponse.desde(alumno);
    }

    @Transactional
    public void eliminar(Integer id) {
        if (matriculaRepository.existsByAlumnoId(id)) {
            throw new NegocioException("No se puede eliminar: el alumno tiene matriculas registradas");
        }
        alumnoRepository.delete(buscarEntidad(id));
    }

    private Alumno buscarEntidad(Integer id) {
        return alumnoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe un alumno con id " + id));
    }

    static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
