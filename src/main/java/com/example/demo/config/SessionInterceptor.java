package com.example.demo.config;


import com.example.demo.controller.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Se ejecuta ANTES de cada peticion protegida.
 * Si no hay sesion, corta la peticion y responde 401 en JSON.
 */
@Component
public class SessionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // false = no crear una sesion nueva si no existe
        HttpSession sesion = request.getSession(false);

        boolean autenticado = sesion != null
                && sesion.getAttribute(AuthController.SESION_USUARIO_ID) != null;

        if (autenticado) {
            return true;   // deja pasar al controller
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"mensaje\":\"Tu sesion expiro. Vuelve a iniciar sesion.\"}");

        return false;      // corta aqui: el controller no se ejecuta
    }
}

