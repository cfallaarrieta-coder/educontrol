package com.example.demo.controller;

import com.example.demo.config.SessionInterceptor;
import com.example.demo.dto.AlumnoRequest;
import com.example.demo.dto.AlumnoResponse;
import com.example.demo.entity.SituacionAlumno;
import com.example.demo.entity.Usuario;
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

    /** GET /api/alumnos?q=quispe&anioId=1&situacion=SIN_MATRICULA */
    @GetMapping
    public List<AlumnoResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer anioId,
            @RequestParam(required = false) SituacionAlumno situacion) {
        return alumnoService.buscar(q, anioId, situacion);
    }

    @GetMapping("/{id}")
    public AlumnoResponse obtener(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer anioId) {
        return alumnoService.obtenerPorId(id, anioId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlumnoResponse crear(
            @Valid @RequestBody AlumnoRequest request,
            @RequestAttribute(value = SessionInterceptor.USUARIO_ACTUAL, required = false) Usuario usuario) {
        return alumnoService.crear(request, usuario != null ? usuario.getUsername() : null);
    }

    @PutMapping("/{id}")
    public AlumnoResponse actualizar(@PathVariable Integer id, @Valid @RequestBody AlumnoRequest request) {
        return alumnoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable Integer id,
            @RequestAttribute(value = SessionInterceptor.USUARIO_ACTUAL, required = false) Usuario usuario) {
        alumnoService.eliminar(id, usuario != null ? usuario.getUsername() : null);
    }
}
