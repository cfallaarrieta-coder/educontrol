package com.example.demo.config;

import com.example.demo.service.RedisService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Configuración de Redis para EduControl (REDIS_SPEC.md):
 * - Suscribe la aplicación al canal Pub/Sub "educontrol:canal:vacantes" (Punto 4: Megáfono)
 *   si el servidor de Redis externo está disponible.
 * - Soporta tanto despliegue en Railway (REDIS_URL) como esquemas Master-Replica con Sentinel
 *   (SPRING_DATA_REDIS_SENTINEL_MASTER / SPRING_DATA_REDIS_SENTINEL_NODES).
 */
@Configuration
public class RedisConfig {

    private RedisMessageListenerContainer contenedorPubSub;

    @Bean
    public ApplicationRunner inicializarSuscripcionRedisPubSub(
            RedisConnectionFactory connectionFactory,
            RedisService redisService,
            @Value("${app.redis.external-enabled:true}") boolean externalEnabled) {
        return args -> {
            if (!externalEnabled) {
                return;
            }
            try (RedisConnection conn = connectionFactory.getConnection()) {
                String pong = conn.ping();
                if (pong != null) {
                    contenedorPubSub = new RedisMessageListenerContainer();
                    contenedorPubSub.setConnectionFactory(connectionFactory);
                    contenedorPubSub.addMessageListener(
                            (message, pattern) -> {
                                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                                redisService.procesarMensajePubSubRecibido(body);
                            },
                            new ChannelTopic(RedisService.CANAL_VACANTES)
                    );
                    contenedorPubSub.afterPropertiesSet();
                    contenedorPubSub.start();
                    System.out.println(">>> Redis conectado exitosamente (Cache + Sesiones + Pub/Sub + Candados activos)");
                }
            } catch (Exception e) {
                System.out.println(">>> Redis externo no detectado en este entorno (" + e.getMessage()
                        + "). Usando pizarra en memoria local como respaldo.");
            }
        };
    }

    @PreDestroy
    public void detenerContenedorPubSub() {
        if (contenedorPubSub != null && contenedorPubSub.isRunning()) {
            try {
                contenedorPubSub.stop();
            } catch (Exception ignored) {
            }
        }
    }
}
