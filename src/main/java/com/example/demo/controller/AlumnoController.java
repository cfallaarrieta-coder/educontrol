package com.example.demo.controller;

import com.example.demo.dto.AlumnoRequest;
import com.example.demo.dto.AlumnoResponse;
import com.example.demo.service.AlumnoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alumnos")
public class AlumnoController {

    private final AlumnoService alumnoService;

    public AlumnoController(AlumnoService alumnoService) {
        this.alumnoService = alumnoService;
    }

    /** GET /api/alumnos?q=quispe  (sin q = todos) */
    @GetMapping
    public List<AlumnoResponse> listar(@RequestParam(required = false) String q) {
        return alumnoService.buscar(q);
    }

    @GetMapping("/{id}")
    public AlumnoResponse obtener(@PathVariable Integer id) {
        return alumnoService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlumnoResponse crear(@Valid @RequestBody AlumnoRequest request) {
        return alumnoService.crear(request);
    }

    @PutMapping("/{id}")
    public AlumnoResponse actualizar(@PathVariable Integer id, @Valid @RequestBody AlumnoRequest request) {
        return alumnoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        alumnoService.eliminar(id);
    }
}
