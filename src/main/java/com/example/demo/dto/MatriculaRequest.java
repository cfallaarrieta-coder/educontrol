package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record MatriculaRequest(

        @NotNull(message = "Selecciona un alumno")
        Integer alumnoId,

        @NotNull(message = "Selecciona una seccion")
        Integer seccionId

) {}
