package com.example.demo.service;

import com.example.demo.dto.GradoRequest;
import com.example.demo.dto.GradoResponse;
import com.example.demo.entity.Grado;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.GradoRepository;
import com.example.demo.repository.SeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GradoService {

    private final GradoRepository gradoRepository;
    private final SeccionRepository seccionRepository;
    private final RedisService redisService;

    public GradoService(GradoRepository gradoRepository,
                        SeccionRepository seccionRepository,
                        RedisService redisService) {
        this.gradoRepository = gradoRepository;
        this.seccionRepository = seccionRepository;
        this.redisService = redisService;
    }

    /**
     * Punto 3 de REDIS_SPEC.md: Catálogo de grados en Redis con TTL de 12 horas.
     */
    @Transactional(readOnly = true)
    public List<GradoResponse> listar() {
        return redisService.obtenerOCalcularGrados(() ->
                gradoRepository.findAllByOrderByOrdenAsc()
                        .stream()
                        .map(GradoResponse::desde)
                        .toList()
        );
    }

    @Transactional
    public GradoResponse crear(GradoRequest request) {
        String nombre = request.nombre().trim();
        if (gradoRepository.existsByNivelAndNombre(request.nivel(), nombre)) {
            throw new NegocioException("Ese grado ya existe en el nivel " + request.nivel());
        }
        GradoResponse creado = GradoResponse.desde(
                gradoRepository.save(new Grado(request.nivel(), nombre, request.orden())));
        redisService.invalidarGrados();
        return creado;
    }

    @Transactional
    public GradoResponse actualizar(Integer id, GradoRequest request) {
        String nombre = request.nombre().trim();
        if (gradoRepository.existsByNivelAndNombreAndIdNot(request.nivel(), nombre, id)) {
            throw new NegocioException("Ese grado ya existe en el nivel " + request.nivel());
        }
        Grado grado = buscarEntidad(id);
        grado.setNivel(request.nivel());
        grado.setNombre(nombre);
        grado.setOrden(request.orden());
        redisService.invalidarGrados();
        return GradoResponse.desde(grado);
    }

    @Transactional
    public void eliminar(Integer id) {
        if (seccionRepository.existsByGradoId(id)) {
            throw new NegocioException("No se puede eliminar: el grado tiene secciones");
        }
        gradoRepository.delete(buscarEntidad(id));
        redisService.invalidarGrados();
    }

    private Grado buscarEntidad(Integer id) {
        return gradoRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe un grado con id " + id));
    }
}
