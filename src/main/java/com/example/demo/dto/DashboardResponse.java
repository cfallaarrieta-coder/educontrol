package com.example.demo.dto;

public record DashboardResponse(

        Integer anio,
        long alumnosRegistrados,
        long matriculados,
        long vacantesLibres,
        long totalSecciones,
        long seccionesLlenas,
        long inicial,
        long primaria,
        long secundaria

) {}
