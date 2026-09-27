package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(

        @NotBlank(message = "El usuario es obligatorio")
        @Size(min = 4, max = 50, message = "El usuario debe tener entre 4 y 50 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$",
                message = "El usuario solo admite letras, numeros y guion bajo")
        String username,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 6, max = 50, message = "La contrasena debe tener al menos 6 caracteres")
        String password,

        @NotBlank(message = "Debes confirmar la contrasena")
        String passwordConfirmacion

) {}
