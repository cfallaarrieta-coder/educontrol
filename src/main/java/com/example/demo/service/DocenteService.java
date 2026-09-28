package com.example.demo.service;

import com.example.demo.dto.DocenteRequest;
import com.example.demo.dto.DocenteResponse;
import com.example.demo.entity.Docente;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.DocenteRepository;
import com.example.demo.repository.SeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.demo.service.AlumnoService.vacioANull;

@Service
public class DocenteService {

    private final DocenteRepository docenteRepository;
    private final SeccionRepository seccionRepository;

    public DocenteService(DocenteRepository docenteRepository,
                          SeccionRepository seccionRepository) {
        this.docenteRepository = docenteRepository;
        this.seccionRepository = seccionRepository;
    }

    @Transactional(readOnly = true)
    public List<DocenteResponse> listar() {
        return docenteRepository.findAllByOrderByApellidosAscNombresAsc()
                .stream()
                .map(DocenteResponse::desde)
                .toList();
    }

    @Transactional
    public DocenteResponse crear(DocenteRequest request) {
        if (docenteRepository.existsByDni(request.dni())) {
            throw new NegocioException("Ya existe un docente con DNI " + request.dni());
        }
        Docente docente = new Docente(request.dni(), request.nombres().trim(), request.apellidos().trim(),
                vacioANull(request.especialidad()), vacioANull(request.telefono()));
        return DocenteResponse.desde(docenteRepository.save(docente));
    }

    @Transactional
    public DocenteResponse actualizar(Integer id, DocenteRequest request) {
        if (docenteRepository.existsByDniAndIdNot(request.dni(), id)) {
            throw new NegocioException("Ya existe otro docente con DNI " + request.dni());
        }
        Docente docente = buscarEntidad(id);
        docente.setDni(request.dni());
        docente.setNombres(request.nombres().trim());
        docente.setApellidos(request.apellidos().trim());
        docente.setEspecialidad(vacioANull(request.especialidad()));
        docente.setTelefono(vacioANull(request.telefono()));
        return DocenteResponse.desde(docente);
    }

    @Transactional
    public void eliminar(Integer id) {
        if (seccionRepository.existsByTutorId(id)) {
            throw new NegocioException("No se puede eliminar: el docente es tutor de una seccion");
        }
        docenteRepository.delete(buscarEntidad(id));
    }

    private Docente buscarEntidad(Integer id) {
        return docenteRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe un docente con id " + id));
    }
}
