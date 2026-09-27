package com.example.demo.controller;


import com.example.demo.dto.LibroRequest;
import com.example.demo.dto.LibroResponse;
import com.example.demo.dto.MensajeResponse;
import com.example.demo.service.LibroService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/libros")
public class LibroController {

    private final LibroService libroService;

    public LibroController(LibroService libroService) {
        this.libroService = libroService;
    }

    /**
     * GET /api/libros          -> todos
     * GET /api/libros?q=borges -> filtra por titulo o autor
     */
    @GetMapping
    public List<LibroResponse> listar(@RequestParam(required = false) String q) {
        return libroService.buscar(q);
    }

    /**
     * GET /api/libros/5
     * Lo usa el boton "Editar" para rellenar el formulario.
     */
    @GetMapping("/{id}")
    public LibroResponse obtener(@PathVariable Integer id) {
        return libroService.obtenerPorId(id);
    }

    /**
     * POST /api/libros
     * Body: { "titulo": "...", "autor": "...", "fechaRegistro": "2026-03-15" }
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibroResponse crear(@Valid @RequestBody LibroRequest request) {
        return libroService.crear(request);
    }

    /**
     * PUT /api/libros/5
     */
    @PutMapping("/{id}")
    public LibroResponse actualizar(@PathVariable Integer id,
                                    @Valid @RequestBody LibroRequest request) {
        return libroService.actualizar(id, request);
    }

    /**
     * DELETE /api/libros/5
     */
    @DeleteMapping("/{id}")
    public MensajeResponse eliminar(@PathVariable Integer id) {
        libroService.eliminar(id);
        return new MensajeResponse("Libro eliminado correctamente");
    }
}
