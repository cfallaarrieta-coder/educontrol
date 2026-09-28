package com.example.demo.dto;

import com.example.demo.entity.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(

        @NotBlank(message = "El usuario es obligatorio")
        @Size(min = 4, max = 50, message = "El usuario debe tener entre 4 y 50 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$",
                message = "El usuario solo admite letras, numeros y guion bajo")
        String username,

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombreCompleto,

        @NotNull(message = "El rol es obligatorio")
        Rol rol,

        @NotNull(message = "Indica si la cuenta esta activa")
        Boolean activo,

        /** Obligatoria al crear. Al editar, si llega vacia no se cambia. */
        @Size(max = 50, message = "La contrasena no puede superar los 50 caracteres")
        String password

) {}
