package com.example.demo.dto;

import com.example.demo.entity.Tarifa;
import java.math.BigDecimal;

public record TarifaResponse(
        Integer id,
        Integer anioEscolarId,
        String nivel,
        BigDecimal montoMatricula,
        BigDecimal montoMensualidad
) {
    public static TarifaResponse desde(Tarifa t) {
        return new TarifaResponse(
                t.getId(),
                t.getAnioEscolar().getId(),
                t.getNivel().name(),
                t.getMontoMatricula(),
                t.getMontoMensualidad()
        );
    }
}
