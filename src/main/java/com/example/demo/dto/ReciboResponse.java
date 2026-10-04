package com.example.demo.dto;

import com.example.demo.entity.Recibo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReciboResponse(
        Integer id,
        String numero,
        Integer matriculaId,
        String alumnoDni,
        String alumnoNombre,
        String apoderado,
        String seccionDescripcion,
        Integer anio,
        String concepto,
        BigDecimal monto,
        LocalDateTime fecha,
        String cajero,
        String metodoPago
) {
    public static ReciboResponse desde(Recibo r) {
        return new ReciboResponse(
                r.getId(),
                r.getNumero(),
                r.getMatricula().getId(),
                r.getMatricula().getAlumno().getDni(),
                r.getMatricula().getAlumno().getNombreCompleto(),
                r.getMatricula().getAlumno().getApoderado(),
                r.getMatricula().getSeccion().getDescripcion(),
                r.getMatricula().getSeccion().getAnioEscolar().getAnio(),
                r.getConcepto(),
                r.getMonto(),
                r.getFecha(),
                r.getCajero(),
                r.getMetodoPago()
        );
    }
}
