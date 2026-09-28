package com.example.demo.service;

import com.example.demo.dto.AnioEscolarRequest;
import com.example.demo.dto.AnioEscolarResponse;
import com.example.demo.entity.AnioEscolar;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.AnioEscolarRepository;
import com.example.demo.repository.SeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnioEscolarService {

    private final AnioEscolarRepository anioRepository;
    private final SeccionRepository seccionRepository;

    public AnioEscolarService(AnioEscolarRepository anioRepository, SeccionRepository seccionRepository) {
        this.anioRepository = anioRepository;
        this.seccionRepository = seccionRepository;
    }

    @Transactional(readOnly = true)
    public List<AnioEscolarResponse> listar() {
        return anioRepository.findAllByOrderByAnioDesc()
                .stream()
                .map(AnioEscolarResponse::desde)
                .toList();
    }

    @Transactional
    public AnioEscolarResponse crear(AnioEscolarRequest request) {
        if (anioRepository.existsByAnio(request.anio())) {
            throw new NegocioException("El anio escolar " + request.anio() + " ya existe");
        }
        return AnioEscolarResponse.desde(anioRepository.save(new AnioEscolar(request.anio(), request.estado())));
    }

    @Transactional
    public AnioEscolarResponse actualizar(Integer id, AnioEscolarRequest request) {
        if (anioRepository.existsByAnioAndIdNot(request.anio(), id)) {
            throw new NegocioException("El anio escolar " + request.anio() + " ya existe");
        }
        AnioEscolar anio = buscarEntidad(id);
        anio.setAnio(request.anio());
        anio.setEstado(request.estado());
        return AnioEscolarResponse.desde(anio);
    }

    @Transactional
    public void eliminar(Integer id) {
        if (seccionRepository.existsByAnioEscolarId(id)) {
            throw new NegocioException("No se puede eliminar: el anio escolar tiene secciones");
        }
        anioRepository.delete(buscarEntidad(id));
    }

    private AnioEscolar buscarEntidad(Integer id) {
        return anioRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe el anio escolar " + id));
    }
}
