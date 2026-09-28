package com.example.demo.dto;

import com.example.demo.entity.AnioEscolar;
import com.example.demo.entity.EstadoAnio;

public record AnioEscolarResponse(

        Integer id,
        Integer anio,
        EstadoAnio estado

) {
    public static AnioEscolarResponse desde(AnioEscolar a) {
        return new AnioEscolarResponse(a.getId(), a.getAnio(), a.getEstado());
    }
}
