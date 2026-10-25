package com.example.demo.config;

import com.example.demo.controller.AuthController;
import com.example.demo.service.RedisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket con STOMP.
 *
 *  - El navegador se conecta a  ws://host/ws
 *  - Se suscribe a  /topic/vacantes, /topic/actividad, /topic/dashboard
 *  - Solo el SERVIDOR publica (NotificadorWebSocket); el cliente solo escucha.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Dominios desde los que se acepta el WebSocket. Detras de un proxy (Railway)
     * la comparacion automatica "mismo origen" falla, por eso se declaran aqui.
     */
    private final String[] origenesPermitidos;
    private final RedisService redisService;

    public WebSocketConfig(@Value("${app.websocket.origenes}") String[] origenesPermitidos,
                           RedisService redisService) {
        this.origenesPermitidos = origenesPermitidos;
        this.redisService = redisService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origenesPermitidos)
                .addInterceptors(new SoloConSesion(redisService));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");        // broker en memoria
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Descarta cualquier SEND del navegador: si no, un usuario podria
     * publicar vacantes falsas directamente en /topic/vacantes.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acceso =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                boolean esEnvio = acceso != null && StompCommand.SEND.equals(acceso.getCommand());
                return esEnvio ? null : message;
            }
        });
    }

    /** Solo acepta el handshake si hay una sesion HTTP o sesion en Redis con usuario logueado. */
    private static class SoloConSesion implements HandshakeInterceptor {

        private final RedisService redisService;

        SoloConSesion(RedisService redisService) {
            this.redisService = redisService;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servlet) {
                Integer usuarioId = AuthController.resolverUsuarioId(servlet.getServletRequest(), redisService);
                return usuarioId != null;
            }
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
