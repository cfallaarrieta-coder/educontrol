package com.example.demo.dto;

import com.example.demo.entity.EstadoMatricula;
import com.example.demo.entity.Matricula;

import java.time.LocalDateTime;
import java.util.List;

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
        String registradoPor,
        LocalDateTime fechaAnulacion,
        String anuladoPor,
        String motivoAnulacion,
        boolean tienePagos,
        List<ReciboResponse> boletasDevolucion

) {
    public static MatriculaResponse desde(Matricula m) {
        return desde(m, List.of());
    }

    public static MatriculaResponse desde(Matricula m, List<ReciboResponse> boletasDevolucion) {
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
                m.getRegistradoPor(),
                m.getFechaAnulacion(),
                m.getAnuladoPor(),
                m.getMotivoAnulacion(),
                m.getReciboMatricula() != null,
                boletasDevolucion != null ? boletasDevolucion : List.of()
        );
    }
}
