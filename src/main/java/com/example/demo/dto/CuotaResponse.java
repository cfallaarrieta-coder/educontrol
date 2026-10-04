package com.example.demo.dto;

import com.example.demo.entity.Cuota;
import com.example.demo.entity.EstadoCuota;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CuotaResponse(
        Integer id,
        Integer numeroCuota,
        String mes,
        BigDecimal monto,
        EstadoCuota estado,
        LocalDate fechaVencimiento,
        boolean pagable,
        Integer reciboId,
        String reciboNumero,
        LocalDateTime fechaPago
) {
    public static CuotaResponse desde(Cuota c, boolean pagable) {
        return new CuotaResponse(
                c.getId(),
                c.getNumeroCuota(),
                c.getMes(),
                c.getMonto(),
                c.getEstado(),
                c.getFechaVencimiento(),
                pagable,
                c.getRecibo() != null ? c.getRecibo().getId() : null,
                c.getRecibo() != null ? c.getRecibo().getNumero() : null,
                c.getRecibo() != null ? c.getRecibo().getFecha() : null
        );
    }
}
