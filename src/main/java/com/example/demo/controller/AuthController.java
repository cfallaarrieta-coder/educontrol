package com.example.demo.controller;


import com.example.demo.dto.CambioPasswordRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.MensajeResponse;
import com.example.demo.dto.RegistroRequest;
import com.example.demo.dto.UsuarioResponse;
import com.example.demo.entity.Usuario;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Clave con la que guardamos el id del usuario dentro de la sesion. */
    public static final String SESION_USUARIO_ID = "usuarioId";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/registro
     * Body: { "username": "jean", "password": "123456", "passwordConfirmacion": "123456" }
     *
     * Ruta publica: cualquiera puede crearse una cuenta.
     */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

    /**
     * POST /api/auth/login
     * Body: { "username": "admin", "password": "123456" }
     */
    @PostMapping("/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest request,
                                 HttpSession sesion) {

        Usuario usuario = authService.login(request);

        // A partir de aqui el navegador recibe una cookie JSESSIONID
        // y el servidor recuerda quien es en cada peticion siguiente.
        sesion.setAttribute(SESION_USUARIO_ID, usuario.getId());

        return UsuarioResponse.desde(usuario);
    }

    /**
     * POST /api/auth/logout
     * Destruye la sesion en el servidor.
     */
    @PostMapping("/logout")
    public MensajeResponse logout(HttpSession sesion) {
        sesion.invalidate();
        return new MensajeResponse("Sesion cerrada correctamente");
    }

    /**
     * GET /api/auth/sesion
     * Lo usa el frontend al cargar la pagina: si responde 401,
     * redirige al login.
     */
    @GetMapping("/sesion")
    public ResponseEntity<?> sesionActual(HttpSession sesion) {

        Integer usuarioId = (Integer) sesion.getAttribute(SESION_USUARIO_ID);

        if (usuarioId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new MensajeResponse("No hay una sesion activa"));
        }

        return ResponseEntity.ok(authService.obtenerPorId(usuarioId));
    }

    /**
     * POST /api/auth/password
     * Cambia la contrasena del usuario logueado.
     */
    @PostMapping("/password")
    public MensajeResponse cambiarPassword(@Valid @RequestBody CambioPasswordRequest request,
                                           HttpSession sesion) {

        Integer usuarioId = (Integer) sesion.getAttribute(SESION_USUARIO_ID);

        authService.cambiarPassword(usuarioId, request);

        // Por seguridad se cierra la sesion: debe volver a entrar con la clave nueva.
        sesion.invalidate();

        return new MensajeResponse("Contrasena actualizada. Inicia sesion nuevamente.");
    }
}
