package com.example.demo.dto;

import com.example.demo.entity.Libro;

import java.time.LocalDate;

public record LibroResponse(

        Integer id,
        String titulo,
        String autor,
        LocalDate fechaRegistro

) {
    public static LibroResponse desde(Libro libro) {
        return new LibroResponse(
                libro.getId(),
                libro.getTitulo(),
                libro.getAutor(),
                libro.getFechaRegistro()
        );
    }
}