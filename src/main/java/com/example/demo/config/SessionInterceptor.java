package com.example.demo.config;

import com.example.demo.controller.AuthController;
import com.example.demo.entity.Usuario;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.service.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Se ejecuta ANTES de cada peticion protegida.
 *
 * Punto 1 y Punto 2 de REDIS_SPEC.md:
 *  - Primero verifica la sesion y el estado del usuario en la "pizarra" de Redis (menos de 1ms)
 *    sin ir a MySQL en cada clic.
 *  - Si no está en Redis (o vencieron sus 15 min de TTL), consulta MySQL y lo anota en Redis.
 *  - Si el administrador desactiva al usuario en UsuarioService, su clave se borra de Redis
 *    al instante y su acceso se corta de inmediato con 401.
 */
@Component
public class SessionInterceptor implements HandlerInterceptor {

    /** Nombre del atributo del request donde queda el usuario logueado. */
    public static final String USUARIO_ACTUAL = "usuarioActual";

    private final UsuarioRepository usuarioRepository;
    private final RedisService redisService;

    public SessionInterceptor(UsuarioRepository usuarioRepository, RedisService redisService) {
        this.usuarioRepository = usuarioRepository;
        this.redisService = redisService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Integer usuarioId = AuthController.resolverUsuarioId(request, redisService);

        // Consulta primero en Redis (TTL 15 min); solo va a MySQL si no está en la pizarra.
        Usuario usuario = usuarioId == null
                ? null
                : redisService.obtenerUsuarioActivo(usuarioId, () -> usuarioRepository.findById(usuarioId));

        if (usuario != null) {
            request.setAttribute(USUARIO_ACTUAL, usuario);
            return true;   // deja pasar al controller
        }

        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            redisService.eliminarSesion(sesion.getId());
            sesion.invalidate();
        }
        String tokenCookie = AuthController.extraerCookieSesion(request);
        if (tokenCookie != null) {
            redisService.eliminarSesion(tokenCookie);
            AuthController.escribirCookieSesion(response, "", 0);
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"mensaje\":\"Tu sesion expiro. Vuelve a iniciar sesion.\"}");

        return false;      // corta aqui: el controller no se ejecuta
    }
}
