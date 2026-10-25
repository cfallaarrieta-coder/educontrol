package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AnularMatriculaRequest(
        @NotBlank(message = "El motivo de anulacion es obligatorio")
        @Size(max = 200, message = "El motivo no puede exceder los 200 caracteres")
        String motivo,

        List<Integer> recibosADevolverIds,

        String metodoDevolucion
) {}
