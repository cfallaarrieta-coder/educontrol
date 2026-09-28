package com.example.demo.dto;

import com.example.demo.entity.Nivel;
import com.example.demo.entity.Seccion;
import com.example.demo.entity.Turno;

public record SeccionResponse(

        Integer id,
        Integer anioEscolarId,
        Integer anio,
        Integer gradoId,
        String grado,
        Nivel nivel,
        String letra,
        String descripcion,
        Turno turno,
        String aula,
        Integer tutorId,
        String tutor,
        Integer vacantesMaximas,
        Integer vacantesDisponibles,
        Integer matriculados

) {
    public static SeccionResponse desde(Seccion s) {
        return new SeccionResponse(
                s.getId(),
                s.getAnioEscolar().getId(),
                s.getAnioEscolar().getAnio(),
                s.getGrado().getId(),
                s.getGrado().getDescripcion(),
                s.getGrado().getNivel(),
                s.getLetra(),
                s.getDescripcion(),
                s.getTurno(),
                s.getAula(),
                s.getTutor() == null ? null : s.getTutor().getId(),
                s.getTutor() == null ? null : s.getTutor().getNombreCompleto(),
                s.getVacantesMaximas(),
                s.getVacantesDisponibles(),
                s.getMatriculados()
        );
    }
}
