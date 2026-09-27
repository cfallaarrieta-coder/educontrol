package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Decide QUE rutas protege el SessionInterceptor.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessionInterceptor sessionInterceptor;

    public WebConfig(SessionInterceptor sessionInterceptor) {
        this.sessionInterceptor = sessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(sessionInterceptor)
                // protege toda la API...
                .addPathPatterns("/api/**")
                // ...menos estas, que deben funcionar SIN sesion
                .excludePathPatterns(
                        "/api/auth/registro",
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/auth/sesion"
                );
    }
}
