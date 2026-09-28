package com.example.demo.config;


import com.example.demo.controller.AuthController;
import com.example.demo.entity.Usuario;
import com.example.demo.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Se ejecuta ANTES de cada peticion protegida.
 * Si no hay sesion (o la cuenta fue desactivada), corta la peticion y responde 401 en JSON.
 * Si todo va bien, deja el Usuario en el request para los controllers y el AdminInterceptor.
 */
@Component
public class SessionInterceptor implements HandlerInterceptor {

    /** Nombre del atributo del request donde queda el usuario logueado. */
    public static final String USUARIO_ACTUAL = "usuarioActual";

    private final UsuarioRepository usuarioRepository;

    public SessionInterceptor(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // false = no crear una sesion nueva si no existe
        HttpSession sesion = request.getSession(false);

        Integer usuarioId = sesion == null
                ? null
                : (Integer) sesion.getAttribute(AuthController.SESION_USUARIO_ID);

        // Se relee de la BD: si el admin desactiva la cuenta, el acceso se corta al instante.
        Usuario usuario = usuarioId == null
                ? null
                : usuarioRepository.findById(usuarioId).filter(Usuario::estaActivo).orElse(null);

        if (usuario != null) {
            request.setAttribute(USUARIO_ACTUAL, usuario);
            return true;   // deja pasar al controller
        }

        if (sesion != null) {
            sesion.invalidate();
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"mensaje\":\"Tu sesion expiro. Vuelve a iniciar sesion.\"}");

        return false;      // corta aqui: el controller no se ejecuta
    }
}

