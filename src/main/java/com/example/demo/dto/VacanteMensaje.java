package com.example.demo.dto;

import com.example.demo.entity.Seccion;

/** Lo que viaja por WebSocket (/topic/vacantes) cuando cambian las vacantes de una seccion. */
public record VacanteMensaje(

        Integer seccionId,
        Integer vacantesDisponibles,
        Integer vacantesMaximas

) {
    public static VacanteMensaje desde(Seccion s) {
        return new VacanteMensaje(s.getId(), s.getVacantesDisponibles(), s.getVacantesMaximas());
    }
}
