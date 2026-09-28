package com.example.demo.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record AlumnoRequest(

        @NotBlank(message = "El DNI es obligatorio")
        @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 digitos")
        String dni,

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 80, message = "Los nombres no pueden superar los 80 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 80, message = "Los apellidos no pueden superar los 80 caracteres")
        String apellidos,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser pasada")
        LocalDate fechaNacimiento,

        @NotBlank(message = "El nombre del apoderado es obligatorio")
        @Size(max = 120, message = "El apoderado no puede superar los 120 caracteres")
        String apoderado,

        @Pattern(regexp = "^$|\\d{6,15}", message = "El telefono solo admite numeros (6 a 15)")
        String telefonoApoderado

) {}
