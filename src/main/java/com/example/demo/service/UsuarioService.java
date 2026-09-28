package com.example.demo.service;

import com.example.demo.dto.UsuarioRequest;
import com.example.demo.dto.UsuarioResponse;
import com.example.demo.entity.Usuario;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Mantenimiento de cuentas (solo ADMIN). */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAllByOrderByUsernameAsc()
                .stream()
                .map(UsuarioResponse::desde)
                .toList();
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        String username = request.username().trim();
        if (usuarioRepository.existsByUsername(username)) {
            throw new NegocioException("Ese nombre de usuario ya esta registrado");
        }
        validarPassword(request.password());

        Usuario usuario = new Usuario(username, passwordEncoder.encode(request.password()),
                request.nombreCompleto().trim(), request.rol());
        usuario.setActivo(request.activo());

        return UsuarioResponse.desde(usuarioRepository.save(usuario));
    }

    /**
     * @param actualId el admin que hace el cambio: no puede quitarse a si mismo
     *                 el rol ADMIN ni desactivarse (se quedaria sin acceso).
     */
    @Transactional
    public UsuarioResponse actualizar(Integer id, UsuarioRequest request, Integer actualId) {
        String username = request.username().trim();
        if (usuarioRepository.existsByUsernameAndIdNot(username, id)) {
            throw new NegocioException("Ese nombre de usuario ya esta registrado");
        }

        Usuario usuario = buscarEntidad(id);

        if (id.equals(actualId) && (!request.activo() || !request.rol().equals(usuario.getRol()))) {
            throw new NegocioException("No puedes desactivarte ni cambiar tu propio rol");
        }

        usuario.setUsername(username);
        usuario.setNombreCompleto(request.nombreCompleto().trim());
        usuario.setRol(request.rol());
        usuario.setActivo(request.activo());

        // Contrasena vacia al editar = se mantiene la actual.
        if (request.password() != null && !request.password().isBlank()) {
            validarPassword(request.password());
            usuario.setPassword(passwordEncoder.encode(request.password()));
        }

        return UsuarioResponse.desde(usuario);
    }

    @Transactional
    public void eliminar(Integer id, Integer actualId) {
        if (id.equals(actualId)) {
            throw new NegocioException("No puedes eliminar tu propia cuenta");
        }
        usuarioRepository.delete(buscarEntidad(id));
    }

    private void validarPassword(String password) {
        if (password == null || password.length() < 6) {
            throw new NegocioException("La contrasena debe tener al menos 6 caracteres");
        }
    }

    private Usuario buscarEntidad(Integer id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe el usuario " + id));
    }
}
