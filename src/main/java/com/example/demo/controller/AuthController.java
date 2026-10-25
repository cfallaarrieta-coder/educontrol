package com.example.demo.controller;

import com.example.demo.dto.CambioPasswordRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.MensajeResponse;
import com.example.demo.dto.UsuarioResponse;
import com.example.demo.entity.Usuario;
import com.example.demo.service.AuthService;
import com.example.demo.service.RedisService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Clave con la que guardamos el id del usuario dentro de la sesion. */
    public static final String SESION_USUARIO_ID = "usuarioId";

    /** Cookie de respaldo de sesion persistida en Redis (Punto 2 de REDIS_SPEC.md). */
    public static final String COOKIE_SESION_REDIS = "EDUCONTROL_SID";

    private final AuthService authService;
    private final RedisService redisService;

    public AuthController(AuthService authService, RedisService redisService) {
        this.authService = authService;
        this.redisService = redisService;
    }

    /**
     * POST /api/auth/login
     * Body: { "username": "admin", "password": "123456" }
     */
    @PostMapping("/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest request,
                                 HttpSession sesion,
                                 HttpServletResponse response) {

        Usuario usuario = authService.login(request);

        // Guardamos en la sesion HTTP y también en Redis (TTL: 30 minutos de inactividad).
        // Si el backend de EduControl se reinicia o hay varios servidores, la sesion no se pierde.
        sesion.setAttribute(SESION_USUARIO_ID, usuario.getId());
        redisService.guardarSesion(sesion.getId(), usuario.getId());
        escribirCookieSesion(response, sesion.getId(), RedisService.TTL_SESION.toSeconds());

        return UsuarioResponse.desde(usuario);
    }

    /**
     * POST /api/auth/logout
     * Destruye la sesion en el servidor y la borra al instante en Redis.
     */
    @PostMapping("/logout")
    public MensajeResponse logout(HttpServletRequest request,
                                  HttpServletResponse response,
                                  HttpSession sesion) {
        String tokenCookie = extraerCookieSesion(request);
        if (tokenCookie != null) {
            redisService.eliminarSesion(tokenCookie);
        }
        if (sesion != null) {
            redisService.eliminarSesion(sesion.getId());
            sesion.invalidate();
        }
        escribirCookieSesion(response, "", 0);
        return new MensajeResponse("Sesion cerrada correctamente");
    }

    /**
     * GET /api/auth/sesion
     * Lo usa el frontend al cargar la pagina: si responde 401,
     * redirige al login.
     */
    @GetMapping("/sesion")
    public ResponseEntity<?> sesionActual(HttpServletRequest request,
                                          HttpServletResponse response) {

        Integer usuarioId = resolverUsuarioId(request, redisService);

        if (usuarioId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new MensajeResponse("No hay una sesion activa"));
        }

        UsuarioResponse usuario = authService.obtenerPorIdActivo(usuarioId);
        if (usuario == null) {
            HttpSession sesion = request.getSession(false);
            if (sesion != null) {
                redisService.eliminarSesion(sesion.getId());
                sesion.invalidate();
            }
            String tokenCookie = extraerCookieSesion(request);
            if (tokenCookie != null) {
                redisService.eliminarSesion(tokenCookie);
            }
            escribirCookieSesion(response, "", 0);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new MensajeResponse("No hay una sesion activa"));
        }

        return ResponseEntity.ok(usuario);
    }

    /**
     * POST /api/auth/password
     * Cambia la contrasena del usuario logueado y borra su sesion en Redis al instante.
     */
    @PostMapping("/password")
    public MensajeResponse cambiarPassword(@Valid @RequestBody CambioPasswordRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse,
                                           HttpSession sesion) {

        Integer usuarioId = resolverUsuarioId(httpRequest, redisService);

        authService.cambiarPassword(usuarioId, request);

        // Por seguridad se cierra la sesion en memoria y en Redis: debe volver a entrar con la clave nueva.
        String tokenCookie = extraerCookieSesion(httpRequest);
        if (tokenCookie != null) {
            redisService.eliminarSesion(tokenCookie);
        }
        if (sesion != null) {
            redisService.eliminarSesion(sesion.getId());
            sesion.invalidate();
        }
        escribirCookieSesion(httpResponse, "", 0);

        return new MensajeResponse("Contrasena actualizada. Inicia sesion nuevamente.");
    }

    /**
     * Resuelve el id del usuario desde Redis (renovando los 30 minutos de inactividad)
     * o desde la sesion HTTP. Si el servidor se reinició pero la sesion sigue viva en Redis,
     * la restaura transparentemente en el HttpSession actual.
     */
    public static Integer resolverUsuarioId(HttpServletRequest request, RedisService redisService) {
        if (request == null) {
            return null;
        }
        HttpSession sesion = request.getSession(false);
        String tokenCookie = extraerCookieSesion(request);

        // 1) Si viene la cookie de sesion de Redis, validamos y renovamos su TTL de 30m en Redis
        if (tokenCookie != null) {
            Integer idDesdeRedis = redisService.obtenerYRenovarSesion(tokenCookie);
            if (idDesdeRedis != null) {
                HttpSession sesionActiva = sesion != null ? sesion : request.getSession(true);
                sesionActiva.setAttribute(SESION_USUARIO_ID, idDesdeRedis);
                return idDesdeRedis;
            }
        }

        // 2) Si hay HttpSession en memoria (por ejemplo en tests con MockHttpSession o misma instancia)
        if (sesion != null) {
            Integer idDesdeRedisPorJSession = redisService.obtenerYRenovarSesion(sesion.getId());
            if (idDesdeRedisPorJSession != null) {
                sesion.setAttribute(SESION_USUARIO_ID, idDesdeRedisPorJSession);
                return idDesdeRedisPorJSession;
            }
            Integer idEnAtributo = (Integer) sesion.getAttribute(SESION_USUARIO_ID);
            if (idEnAtributo != null) {
                redisService.guardarSesion(sesion.getId(), idEnAtributo);
                return idEnAtributo;
            }
        }

        return null;
    }

    public static String extraerCookieSesion(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_SESION_REDIS.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue().trim();
            }
        }
        return null;
    }

    public static void escribirCookieSesion(HttpServletResponse response, String valor, long maxAgeSegundos) {
        if (response == null) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(COOKIE_SESION_REDIS, valor == null ? "" : valor)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAgeSegundos)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
