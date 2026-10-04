package com.example.demo.dto;

import com.example.demo.entity.EstadoMatricula;

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

        // Derecho de Matricula
        BigDecimal costoMatricula,
        boolean matriculaPagada,
        Integer reciboMatriculaId,
        String reciboMatriculaNumero,
        LocalDateTime fechaPagoMatricula,

        // Cuotas de mensualidades (Marzo a Diciembre)
        List<CuotaResponse> cuotas,
        BigDecimal totalDeuda,
        BigDecimal totalPagado
) {}
