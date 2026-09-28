package com.example.demo.dto;

import com.example.demo.entity.Rol;
import com.example.demo.entity.Usuario;

public record UsuarioResponse(

        Integer id,
        String username,
        String nombreCompleto,
        Rol rol,
        boolean activo

) {
    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol(),
                usuario.estaActivo()
        );
    }
}
