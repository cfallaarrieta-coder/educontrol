package com.example.demo.controller;

import com.example.demo.config.SessionInterceptor;
import com.example.demo.dto.MatriculaRequest;
import com.example.demo.dto.MatriculaResponse;
import com.example.demo.dto.TrasladoRequest;
import com.example.demo.entity.EstadoMatricula;
import com.example.demo.entity.Usuario;
import com.example.demo.service.MatriculaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matriculas")
public class MatriculaController {

    private final MatriculaService matriculaService;

    public MatriculaController(MatriculaService matriculaService) {
        this.matriculaService = matriculaService;
    }

    /** GET /api/matriculas?anioId=1&seccionId=3&estado=ACTIVA&q=quispe (todos opcionales) */
    @GetMapping
    public List<MatriculaResponse> listar(@RequestParam(required = false) Integer anioId,
                                          @RequestParam(required = false) Integer seccionId,
                                          @RequestParam(required = false) EstadoMatricula estado,
                                          @RequestParam(required = false) String q) {
        return matriculaService.listar(anioId, seccionId, estado, q);
    }

    /** GET /api/matriculas/nomina/{seccionId} */
    @GetMapping("/nomina/{seccionId}")
    public List<MatriculaResponse> nomina(@PathVariable Integer seccionId) {
        return matriculaService.nomina(seccionId);
    }

    /** POST /api/matriculas  { "alumnoId": 1, "seccionId": 5 } */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatriculaResponse matricular(@Valid @RequestBody MatriculaRequest request,
                                        @RequestAttribute(SessionInterceptor.USUARIO_ACTUAL) Usuario usuario) {
        return matriculaService.matricular(request, usuario.getUsername());
    }

    /** POST /api/matriculas/{id}/anular */
    @PostMapping("/{id}/anular")
    public MatriculaResponse anular(@PathVariable Integer id,
                                    @RequestAttribute(SessionInterceptor.USUARIO_ACTUAL) Usuario usuario) {
        return matriculaService.anular(id, usuario.getUsername());
    }

    /** POST /api/matriculas/{id}/traslado  { "seccionDestinoId": 6 } */
    @PostMapping("/{id}/traslado")
    public MatriculaResponse trasladar(@PathVariable Integer id,
                                       @Valid @RequestBody TrasladoRequest request,
                                       @RequestAttribute(SessionInterceptor.USUARIO_ACTUAL) Usuario usuario) {
        return matriculaService.trasladar(id, request.seccionDestinoId(), usuario.getUsername());
    }
}
