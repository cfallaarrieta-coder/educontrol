package com.example.demo.controller;

import com.example.demo.dto.GradoRequest;
import com.example.demo.dto.GradoResponse;
import com.example.demo.service.GradoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grados")
public class GradoController {

    private final GradoService gradoService;

    public GradoController(GradoService gradoService) {
        this.gradoService = gradoService;
    }

    @GetMapping
    public List<GradoResponse> listar() {
        return gradoService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GradoResponse crear(@Valid @RequestBody GradoRequest request) {
        return gradoService.crear(request);
    }

    @PutMapping("/{id}")
    public GradoResponse actualizar(@PathVariable Integer id, @Valid @RequestBody GradoRequest request) {
        return gradoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        gradoService.eliminar(id);
    }
}
