package com.example.demo.controller;

import com.example.demo.dto.MensajeResponse;
import com.example.demo.dto.TarifaRequest;
import com.example.demo.dto.TarifaResponse;
import com.example.demo.entity.Nivel;
import com.example.demo.service.TarifaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tarifas")
public class TarifaController {

    private final TarifaService tarifaService;

    public TarifaController(TarifaService tarifaService) {
        this.tarifaService = tarifaService;
    }

    @GetMapping
    public List<TarifaResponse> listar(@RequestParam Integer anioId) {
        return tarifaService.listar(anioId);
    }

    @PutMapping("/{anioId}/{nivel}")
    public TarifaResponse guardar(@PathVariable Integer anioId,
                                  @PathVariable Nivel nivel,
                                  @Valid @RequestBody TarifaRequest request) {
        return tarifaService.guardar(anioId, nivel, request);
    }

    @PostMapping("/{anioId}/{nivel}/aplicar")
    public MensajeResponse aplicar(@PathVariable Integer anioId, @PathVariable Nivel nivel) {
        int actualizadas = tarifaService.aplicarACuotasPendientes(anioId, nivel);
        return new MensajeResponse("Se actualizaron " + actualizadas + " cuotas pendientes con el nuevo monto.");
    }

    @PostMapping("/copiar")
    public MensajeResponse copiar(@RequestParam Integer origenId, @RequestParam Integer destinoId) {
        tarifaService.copiarDesdeAnio(origenId, destinoId);
        return new MensajeResponse("Tarifas copiadas correctamente.");
    }
}
