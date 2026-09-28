package com.example.demo.dto;

import com.example.demo.entity.EstadoAnio;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AnioEscolarRequest(

        @NotNull(message = "El anio es obligatorio")
        @Min(value = 2000, message = "Anio no valido")
        @Max(value = 2100, message = "Anio no valido")
        Integer anio,

        @NotNull(message = "El estado es obligatorio")
        EstadoAnio estado

) {}
