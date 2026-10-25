package com.example.demo.dto;

import com.example.demo.entity.EstadoMatricula;
import com.example.demo.entity.EstadoRecibo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CuentaCorrienteResponse(
        Integer matriculaId,
        Integer alumnoId,
        String alumnoDni,
        String alumnoNombre,
        String apoderado,
        String seccionDescripcion,
        String nivel,
        Integer anio,
        EstadoMatricula estadoMatricula,
        String motivoAnulacion,
        LocalDateTime fechaAnulacion,
        String anuladoPor,

        // Derecho de Matricula
        BigDecimal costoMatricula,
        boolean matriculaPagada,
        Integer reciboMatriculaId,
        String reciboMatriculaNumero,
        LocalDateTime fechaPagoMatricula,
        EstadoRecibo reciboMatriculaEstado,
        Integer boletaDevolucionMatriculaId,
        String boletaDevolucionMatriculaNumero,

        // Cuotas de mensualidades (Marzo a Diciembre)
        List<CuotaResponse> cuotas,
        List<ReciboResponse> recibos,
        BigDecimal totalDeuda,
        BigDecimal totalPagado,
        BigDecimal totalDevuelto
) {}
