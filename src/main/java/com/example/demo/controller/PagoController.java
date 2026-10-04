package com.example.demo.controller;

import com.example.demo.config.SessionInterceptor;
import com.example.demo.dto.CuentaCorrienteResponse;
import com.example.demo.dto.PagoRequest;
import com.example.demo.dto.ReciboResponse;
import com.example.demo.entity.Usuario;
import com.example.demo.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    /** GET /api/pagos/cuenta/{matriculaId} */
    @GetMapping("/cuenta/{matriculaId}")
    public CuentaCorrienteResponse obtenerCuentaCorriente(@PathVariable Integer matriculaId) {
        return pagoService.obtenerCuentaCorriente(matriculaId);
    }

    /** POST /api/pagos/matricula/{matriculaId} */
    @PostMapping("/matricula/{matriculaId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ReciboResponse pagarMatricula(@PathVariable Integer matriculaId,
                                         @RequestBody(required = false) PagoRequest request,
                                         @RequestAttribute(SessionInterceptor.USUARIO_ACTUAL) Usuario usuario) {
        return pagoService.pagarMatricula(matriculaId, request, usuario.getUsername());
    }

    /** POST /api/pagos/cuota/{cuotaId} */
    @PostMapping("/cuota/{cuotaId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ReciboResponse pagarCuota(@PathVariable Integer cuotaId,
                                     @RequestBody(required = false) PagoRequest request,
                                     @RequestAttribute(SessionInterceptor.USUARIO_ACTUAL) Usuario usuario) {
        return pagoService.pagarCuota(cuotaId, request, usuario.getUsername());
    }

    /** GET /api/pagos/recibos?anioId=1&matriculaId=5&q=78123401 */
    @GetMapping("/recibos")
    public List<ReciboResponse> listarRecibos(@RequestParam(required = false) Integer anioId,
                                              @RequestParam(required = false) Integer matriculaId,
                                              @RequestParam(required = false) String q) {
        return pagoService.listarRecibos(anioId, matriculaId, q);
    }

    /** GET /api/pagos/recibos/{id} */
    @GetMapping("/recibos/{id}")
    public ReciboResponse obtenerRecibo(@PathVariable Integer id) {
        return pagoService.obtenerRecibo(id);
    }
}
