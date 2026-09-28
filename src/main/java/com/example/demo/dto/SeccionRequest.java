package com.example.demo.dto;

import com.example.demo.entity.Turno;
import jakarta.validation.constraints.*;

public record SeccionRequest(

        @NotNull(message = "El anio escolar es obligatorio")
        Integer anioEscolarId,

        @NotNull(message = "El grado es obligatorio")
        Integer gradoId,

        @NotBlank(message = "La letra de la seccion es obligatoria")
        @Pattern(regexp = "^[A-Za-z]{1,2}$", message = "La seccion debe ser una letra (A, B, C...)")
        String letra,

        @NotNull(message = "El turno es obligatorio")
        Turno turno,

        @Size(max = 30, message = "El aula no puede superar los 30 caracteres")
        String aula,

        /** Opcional. */
        Integer tutorId,

        @NotNull(message = "Las vacantes son obligatorias")
        @Min(value = 1, message = "Debe haber al menos 1 vacante")
        @Max(value = 60, message = "Maximo 60 vacantes por seccion")
        Integer vacantesMaximas

) {}
