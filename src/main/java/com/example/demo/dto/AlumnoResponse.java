package com.example.demo.dto;

import com.example.demo.entity.Alumno;

import java.time.LocalDate;

public record AlumnoResponse(

        Integer id,
        String dni,
        String nombres,
        String apellidos,
        String nombreCompleto,
        LocalDate fechaNacimiento,
        String apoderado,
        String telefonoApoderado

) {
    public static AlumnoResponse desde(Alumno a) {
        return new AlumnoResponse(a.getId(), a.getDni(), a.getNombres(), a.getApellidos(),
                a.getNombreCompleto(), a.getFechaNacimiento(), a.getApoderado(), a.getTelefonoApoderado());
    }
}
