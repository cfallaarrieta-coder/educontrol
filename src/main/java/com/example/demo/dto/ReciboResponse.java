package com.example.demo.dto;

import com.example.demo.entity.EstadoRecibo;
import com.example.demo.entity.Recibo;
import com.example.demo.entity.TipoRecibo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReciboResponse(
        Integer id,
        String numero,
        TipoRecibo tipo,
        EstadoRecibo estado,
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
        String metodoPago,
        Integer reciboOrigenId,
        String reciboOrigenNumero,
        LocalDateTime reciboOrigenFecha,
        Integer boletaDevolucionId,
        String boletaDevolucionNumero,
        String motivoAnulacion,
        LocalDateTime fechaAnulacion,
        String anuladoPor
) {
    public static ReciboResponse desde(Recibo r) {
        Recibo origen = r.getReciboOrigen();
        Recibo dev = r.getBoletaDevolucion();
        return new ReciboResponse(
                r.getId(),
                r.getNumero(),
                r.getTipo(),
                r.getEstado(),
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
                r.getMetodoPago(),
                origen != null ? origen.getId() : null,
                origen != null ? origen.getNumero() : null,
                origen != null ? origen.getFecha() : null,
                dev != null ? dev.getId() : null,
                dev != null ? dev.getNumero() : null,
                r.getMotivoAnulacion(),
                r.getFechaAnulacion(),
                r.getAnuladoPor()
        );
    }
}
