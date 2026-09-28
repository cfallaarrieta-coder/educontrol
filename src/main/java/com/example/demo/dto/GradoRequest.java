package com.example.demo.dto;

import com.example.demo.entity.Nivel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GradoRequest(

        @NotNull(message = "El nivel es obligatorio")
        Nivel nivel,

        @NotBlank(message = "El nombre del grado es obligatorio")
        @Size(max = 20, message = "El nombre no puede superar los 20 caracteres")
        String nombre,

        @NotNull(message = "El orden es obligatorio")
        @Min(value = 1, message = "El orden debe ser mayor que 0")
        Integer orden

) {}
