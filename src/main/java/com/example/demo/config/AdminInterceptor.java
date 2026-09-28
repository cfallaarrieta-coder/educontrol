package com.example.demo.config;

import com.example.demo.entity.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Permisos por rol. Corre DESPUES del SessionInterceptor.
 *
 *  - /api/usuarios/**                         -> solo ADMIN (incluso para leer)
 *  - anios, docentes, grados, secciones       -> SECRETARIA solo puede leer (GET)
 *  - alumnos y matriculas                     -> ambos roles (no pasan por aqui)
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Usuario usuario = (Usuario) request.getAttribute(SessionInterceptor.USUARIO_ACTUAL);

        boolean esLectura = "GET".equalsIgnoreCase(request.getMethod());
        boolean soloAdminSiempre = request.getRequestURI().startsWith("/api/usuarios");

        if (usuario.esAdmin() || (esLectura && !soloAdminSiempre)) {
            return true;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"mensaje\":\"Solo la direccion (ADMIN) puede realizar esta accion.\"}");
        return false;
    }
}
