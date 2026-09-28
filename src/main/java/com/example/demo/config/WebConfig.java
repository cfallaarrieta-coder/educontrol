package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Decide QUE rutas protegen los interceptores (se ejecutan en este orden).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessionInterceptor sessionInterceptor;
    private final AdminInterceptor adminInterceptor;

    public WebConfig(SessionInterceptor sessionInterceptor, AdminInterceptor adminInterceptor) {
        this.sessionInterceptor = sessionInterceptor;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 1) ¿Hay sesion?
        registry.addInterceptor(sessionInterceptor)
                // protege toda la API...
                .addPathPatterns("/api/**")
                // ...menos estas, que deben funcionar SIN sesion
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/auth/sesion"
                );

        // 2) ¿Tiene el rol necesario?
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns(
                        "/api/usuarios/**",
                        "/api/anios/**",
                        "/api/docentes/**",
                        "/api/grados/**",
                        "/api/secciones/**"
                );
    }
}
