package com.example.demo.service;

import com.example.demo.dto.CambioPasswordRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegistroRequest;
import com.example.demo.dto.UsuarioResponse;
import com.example.demo.entity.Usuario;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registra una cuenta nueva. No inicia sesion: el usuario
     * debe entrar despues por la pantalla de login.
     */
    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {

        String username = request.username().trim();

        if (usuarioRepository.existsByUsername(username)) {
            throw new NegocioException("Ese nombre de usuario ya esta registrado");
        }

        if (!request.password().equals(request.passwordConfirmacion())) {
            throw new NegocioException("La contrasena y su confirmacion no coinciden");
        }

        // La contrasena NUNCA se guarda tal cual: se guarda su hash.
        Usuario usuario = new Usuario(
                username,
                passwordEncoder.encode(request.password())
        );

        return UsuarioResponse.desde(usuarioRepository.save(usuario));
    }

    /**
     * Valida las credenciales y devuelve el usuario autenticado.
     */
    @Transactional(readOnly = true)
    public Usuario login(LoginRequest request) {

        Usuario usuario = usuarioRepository
                .findByUsername(request.username())
                .orElseThrow(() ->
                        new NegocioException("Usuario o contrasena incorrectos"));

        // matches(textoPlano, hash) -> hashea lo ingresado y lo compara
        boolean claveCorrecta =
                passwordEncoder.matches(request.password(), usuario.getPassword());

        if (!claveCorrecta) {
            // Mismo mensaje que arriba, a proposito: si dijeramos
            // "ese usuario no existe" estariamos revelando que cuentas son validas.
            throw new NegocioException("Usuario o contrasena incorrectos");
        }

        return usuario;
    }

    /**
     * Devuelve los datos del usuario logueado (sin la contrasena).
     */
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Integer usuarioId) {
        return UsuarioResponse.desde(
                usuarioRepository.findById(usuarioId)
                        .orElseThrow(() -> new NegocioException("El usuario no existe")));
    }

    /**
     * Cambia la contrasena del usuario que tiene la sesion abierta.
     */
    @Transactional
    public void cambiarPassword(Integer usuarioId, CambioPasswordRequest request) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NegocioException("El usuario no existe"));

        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPassword())) {
            throw new NegocioException("La contrasena actual no es correcta");
        }

        if (!request.passwordNueva().equals(request.passwordConfirmacion())) {
            throw new NegocioException("La nueva contrasena y su confirmacion no coinciden");
        }

        if (passwordEncoder.matches(request.passwordNueva(), usuario.getPassword())) {
            throw new NegocioException("La nueva contrasena debe ser distinta de la actual");
        }

        usuario.setPassword(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.save(usuario);
    }
}
