package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TarifaRequest(
        @NotNull(message = "El monto de matrícula es obligatorio")
        @DecimalMin(value = "0.0", message = "El monto no puede ser negativo")
        BigDecimal montoMatricula,

        @NotNull(message = "El monto de mensualidad es obligatorio")
        @DecimalMin(value = "0.0", message = "El monto no puede ser negativo")
        BigDecimal montoMensualidad
) {
}
