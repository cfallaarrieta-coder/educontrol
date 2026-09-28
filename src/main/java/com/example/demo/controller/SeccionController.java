package com.example.demo.controller;

import com.example.demo.dto.SeccionRequest;
import com.example.demo.dto.SeccionResponse;
import com.example.demo.service.SeccionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/secciones")
public class SeccionController {

    private final SeccionService seccionService;

    public SeccionController(SeccionService seccionService) {
        this.seccionService = seccionService;
    }

    /** GET /api/secciones?anioId=1&gradoId=4 (filtros opcionales) */
    @GetMapping
    public List<SeccionResponse> listar(@RequestParam(required = false) Integer anioId,
                                        @RequestParam(required = false) Integer gradoId) {
        return seccionService.listar(anioId, gradoId);
    }

    @GetMapping("/{id}")
    public SeccionResponse obtener(@PathVariable Integer id) {
        return seccionService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeccionResponse crear(@Valid @RequestBody SeccionRequest request) {
        return seccionService.crear(request);
    }

    @PutMapping("/{id}")
    public SeccionResponse actualizar(@PathVariable Integer id, @Valid @RequestBody SeccionRequest request) {
        return seccionService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        seccionService.eliminar(id);
    }
}
