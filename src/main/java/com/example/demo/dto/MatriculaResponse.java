package com.example.demo.dto;

import com.example.demo.entity.EstadoMatricula;
import com.example.demo.entity.Matricula;

import java.time.LocalDateTime;

public record MatriculaResponse(

        Integer id,
        Integer alumnoId,
        String alumnoDni,
        String alumno,
        String apoderado,
        String telefonoApoderado,
        Integer seccionId,
        Integer gradoId,
        String seccion,
        Integer anio,
        LocalDateTime fecha,
        EstadoMatricula estado,
        String registradoPor

) {
    public static MatriculaResponse desde(Matricula m) {
        return new MatriculaResponse(
                m.getId(),
                m.getAlumno().getId(),
                m.getAlumno().getDni(),
                m.getAlumno().getNombreCompleto(),
                m.getAlumno().getApoderado(),
                m.getAlumno().getTelefonoApoderado(),
                m.getSeccion().getId(),
                m.getSeccion().getGrado().getId(),
                m.getSeccion().getDescripcion(),
                m.getSeccion().getAnioEscolar().getAnio(),
                m.getFecha(),
                m.getEstado(),
                m.getRegistradoPor()
        );
    }
}
