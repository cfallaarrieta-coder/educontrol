package com.example.demo.controller;

import com.example.demo.dto.AnioEscolarRequest;
import com.example.demo.dto.AnioEscolarResponse;
import com.example.demo.service.AnioEscolarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/anios")
public class AnioEscolarController {

    private final AnioEscolarService anioService;

    public AnioEscolarController(AnioEscolarService anioService) {
        this.anioService = anioService;
    }

    @GetMapping
    public List<AnioEscolarResponse> listar() {
        return anioService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnioEscolarResponse crear(@Valid @RequestBody AnioEscolarRequest request) {
        return anioService.crear(request);
    }

    @PutMapping("/{id}")
    public AnioEscolarResponse actualizar(@PathVariable Integer id, @Valid @RequestBody AnioEscolarRequest request) {
        return anioService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        anioService.eliminar(id);
    }
}
