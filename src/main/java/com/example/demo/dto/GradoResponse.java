package com.example.demo.dto;

import com.example.demo.entity.Grado;
import com.example.demo.entity.Nivel;

public record GradoResponse(

        Integer id,
        Nivel nivel,
        String nombre,
        Integer orden,
        String descripcion

) {
    public static GradoResponse desde(Grado g) {
        return new GradoResponse(g.getId(), g.getNivel(), g.getNombre(), g.getOrden(), g.getDescripcion());
    }
}
