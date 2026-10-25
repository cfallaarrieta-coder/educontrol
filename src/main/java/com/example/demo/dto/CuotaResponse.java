package com.example.demo.dto;

import com.example.demo.entity.Cuota;
import com.example.demo.entity.EstadoCuota;
import com.example.demo.entity.Recibo;

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
        LocalDateTime fechaPago,
        Integer boletaDevolucionId,
        String boletaDevolucionNumero
) {
    public static CuotaResponse desde(Cuota c, boolean pagable) {
        Recibo rec = c.getRecibo();
        Recibo dev = (rec != null) ? rec.getBoletaDevolucion() : null;
        return new CuotaResponse(
                c.getId(),
                c.getNumeroCuota(),
                c.getMes(),
                c.getMonto(),
                c.getEstado(),
                c.getFechaVencimiento(),
                pagable,
                rec != null ? rec.getId() : null,
                rec != null ? rec.getNumero() : null,
                rec != null ? rec.getFecha() : null,
                dev != null ? dev.getId() : null,
                dev != null ? dev.getNumero() : null
        );
    }
}
