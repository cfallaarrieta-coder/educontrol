package com.example.demo.dto;

import com.example.demo.entity.Usuario;
public record UsuarioResponse(

        Integer id,
        String username

) {
    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getUsername()
        );
    }
}