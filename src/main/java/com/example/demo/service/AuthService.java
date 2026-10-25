package com.example.demo.service;

import com.example.demo.dto.CambioPasswordRequest;
import com.example.demo.dto.LoginRequest;
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
    private final RedisService redisService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       RedisService redisService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisService = redisService;
    }

    /**
     * Valida las credenciales y devuelve el usuario autenticado.
     *
     * Punto 1 y Punto 5 de REDIS_SPEC.md:
     *  - Cuenta cuántas veces seguidas se equivocó de contraseña una persona en Redis.
     *    Si falla 5 veces, Redis bloquea nuevos intentos por 15 minutos.
     *  - Si acierta su contraseña, el contador vuelve a cero y se anota en Redis que el usuario
     *    está ACTIVO (TTL: 15 minutos).
     */
    @Transactional(readOnly = true)
    public Usuario login(LoginRequest request) {

        String username = request.username() == null ? "" : request.username().trim();

        // 1) Verifica en Redis si el usuario superó los 5 intentos fallidos en los últimos 15 min
        redisService.verificarLoginNoBloqueado(username);

        Usuario usuario = usuarioRepository
                .findByUsername(username)
                .orElse(null);

        if (usuario == null) {
            redisService.registrarIntentoFallidoLogin(username);
            throw new NegocioException("Usuario o contrasena incorrectos");
        }

        // matches(textoPlano, hash) -> hashea lo ingresado y lo compara
        boolean claveCorrecta =
                passwordEncoder.matches(request.password(), usuario.getPassword());

        if (!claveCorrecta) {
            redisService.registrarIntentoFallidoLogin(username);
            // Mismo mensaje que arriba, a proposito: si dijeramos
            // "ese usuario no existe" estariamos revelando que cuentas son validas.
            throw new NegocioException("Usuario o contrasena incorrectos");
        }

        if (!usuario.estaActivo()) {
            throw new NegocioException("Tu cuenta esta desactivada. Consulta con la direccion.");
        }

        // Si acierta su contraseña, el contador vuelve a cero y se guarda su estado en Redis
        redisService.limpiarIntentosLogin(username);
        redisService.guardarEstadoUsuario(usuario);

        return usuario;
    }

    /**
     * Devuelve los datos del usuario logueado (sin la contrasena).
     */
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Integer usuarioId) {
        Usuario activo = redisService.obtenerUsuarioActivo(
                usuarioId, () -> usuarioRepository.findById(usuarioId));
        if (activo != null) {
            return UsuarioResponse.desde(activo);
        }
        return UsuarioResponse.desde(
                usuarioRepository.findById(usuarioId)
                        .orElseThrow(() -> new NegocioException("El usuario no existe")));
    }

    /**
     * Devuelve los datos del usuario si sigue activo (consultando primero en Redis).
     */
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorIdActivo(Integer usuarioId) {
        Usuario activo = redisService.obtenerUsuarioActivo(
                usuarioId, () -> usuarioRepository.findById(usuarioId));
        return activo == null ? null : UsuarioResponse.desde(activo);
    }

    /**
     * Cambia la contrasena del usuario que tiene la sesion abierta.
     */
    @Transactional
    public void cambiarPassword(Integer usuarioId, CambioPasswordRequest request) {

        if (usuarioId == null) {
            throw new NegocioException("No hay una sesion activa");
        }

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

        // Borra la sesion y el estado en Redis al instante al cambiar la clave
        redisService.invalidarUsuario(usuarioId);
    }
}
