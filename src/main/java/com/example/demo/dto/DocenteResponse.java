package com.example.demo.dto;

import com.example.demo.entity.Docente;

public record DocenteResponse(

        Integer id,
        String dni,
        String nombres,
        String apellidos,
        String nombreCompleto,
        String especialidad,
        String telefono

) {
    public static DocenteResponse desde(Docente d) {
        return new DocenteResponse(d.getId(), d.getDni(), d.getNombres(), d.getApellidos(),
                d.getNombreCompleto(), d.getEspecialidad(), d.getTelefono());
    }
}
