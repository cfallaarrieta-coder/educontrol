package com.example.demo.dto;

import com.example.demo.entity.Alumno;
import com.example.demo.entity.SituacionAlumno;

import java.time.LocalDate;

public record AlumnoResponse(

        Integer id,
        String dni,
        String nombres,
        String apellidos,
        String nombreCompleto,
        LocalDate fechaNacimiento,
        String apoderado,
        String telefonoApoderado,
        SituacionAlumno situacion,
        Integer matriculaActivaId,
        String seccionActual,
        boolean tuvoAnulacionEnAnio

) {
    public static AlumnoResponse desde(Alumno a) {
        return desde(a, SituacionAlumno.SIN_MATRICULA, null, null, false);
    }

    public static AlumnoResponse desde(Alumno a,
                                       SituacionAlumno situacion,
                                       Integer matriculaActivaId,
                                       String seccionActual,
                                       boolean tuvoAnulacionEnAnio) {
        return new AlumnoResponse(
                a.getId(),
                a.getDni(),
                a.getNombres(),
                a.getApellidos(),
                a.getNombreCompleto(),
                a.getFechaNacimiento(),
                a.getApoderado(),
                a.getTelefonoApoderado(),
                situacion,
                matriculaActivaId,
                seccionActual,
                tuvoAnulacionEnAnio
        );
    }
}
