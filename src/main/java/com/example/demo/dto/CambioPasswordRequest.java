package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambioPasswordRequest(

        @NotBlank(message = "Debes ingresar tu contraseña actual")
        String passwordActual,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 6, max = 50, message = "La nueva contraseña debe tener al menos 6 caracteres")
        String passwordNueva,

        @NotBlank(message = "Debes confirmar la nueva contraseña")
        String passwordConfirmacion

) {}
