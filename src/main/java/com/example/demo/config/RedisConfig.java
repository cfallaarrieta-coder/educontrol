package com.example.demo.config;

import com.example.demo.service.RedisService;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.ClientOptions;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configuración de Redis para EduControl (Secciones 3 y 4 de REDIS_SPEC.md):
 * - Soporta Opción 2 completa en Railway: 1 Maestro + 2 Réplicas + 3 Sentinels
 *   con lectura preferente desde Réplicas (ReadFrom.REPLICA_PREFERRED) y escritura en el Maestro.
 * - Soporta también topología Maestro-Réplica directa (REDIS_REPLICA_URLS) y nodo Standalone (REDIS_URL).
 * - Suscribe la aplicación al canal Pub/Sub "educontrol:canal:vacantes" (Punto 4: Megáfono).
 */
@Configuration
public class RedisConfig {

    private RedisMessageListenerContainer contenedorPubSub;

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.url:redis://localhost:6379}") String redisUrl,
            @Value("${spring.data.redis.password:}") String redisPasswordProp,
            @Value("${spring.data.redis.sentinel.master:educontrol-master}") String sentinelMaster,
            @Value("${spring.data.redis.sentinel.nodes:}") String sentinelNodes,
            @Value("${spring.data.redis.sentinel.password:}") String sentinelPassword,
            @Value("${app.redis.replica-urls:}") String replicaUrls) {

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build())
                .autoReconnect(true)
                .build();

        EndpointInfo masterInfo = parsearRedisUri(redisUrl, "localhost", 6379);
        String passwordEfectivo = (redisPasswordProp != null && !redisPasswordProp.isBlank())
                ? redisPasswordProp.trim()
                : masterInfo.password();

        // 1. MODO OPCIÓN 2 (RECOMENDADO EN SPEC): Maestro + 2 Réplicas + 3 Sentinels
        List<RedisNode> nodosSentinel = parsearNodos(sentinelNodes);
        if (!nodosSentinel.isEmpty()) {
            RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
            sentinelConfig.master(sentinelMaster != null && !sentinelMaster.isBlank()
                    ? sentinelMaster.trim()
                    : "educontrol-master");
            for (RedisNode nodo : nodosSentinel) {
                sentinelConfig.addSentinel(nodo);
            }
            if (masterInfo.username() != null && !masterInfo.username().isBlank()) {
                sentinelConfig.setUsername(masterInfo.username());
            }
            if (passwordEfectivo != null && !passwordEfectivo.isBlank()) {
                sentinelConfig.setPassword(RedisPassword.of(passwordEfectivo));
            }
            if (sentinelPassword != null && !sentinelPassword.isBlank()) {
                sentinelConfig.setSentinelPassword(RedisPassword.of(sentinelPassword.trim()));
            }

            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(2))
                    .clientOptions(clientOptions)
                    // Lee consultas rápidas de las Réplicas y escribe cambios en el Maestro (Sección 4 REDIS_SPEC.md)
                    .readFrom(ReadFrom.REPLICA_PREFERRED)
                    .build();

            return new LettuceConnectionFactory(sentinelConfig, clientConfig);
        }

        // 2. MODO MAESTRO + RÉPLICAS ESTÁTICAS (Sin Sentinel, usando REDIS_REPLICA_URLS)
        List<EndpointInfo> replicas = parsearListaUris(replicaUrls);
        if (!replicas.isEmpty()) {
            RedisStaticMasterReplicaConfiguration masterReplicaConfig =
                    new RedisStaticMasterReplicaConfiguration(masterInfo.host(), masterInfo.port());
            if (masterInfo.username() != null && !masterInfo.username().isBlank()) {
                masterReplicaConfig.setUsername(masterInfo.username());
            }
            if (passwordEfectivo != null && !passwordEfectivo.isBlank()) {
                masterReplicaConfig.setPassword(RedisPassword.of(passwordEfectivo));
            }
            for (EndpointInfo replica : replicas) {
                masterReplicaConfig.addNode(replica.host(), replica.port());
            }

            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(2))
                    .clientOptions(clientOptions)
                    .readFrom(ReadFrom.REPLICA_PREFERRED)
                    .build();

            return new LettuceConnectionFactory(masterReplicaConfig, clientConfig);
        }

        // 3. MODO STANDALONE (Respaldo si solo hay 1 nodo configurado)
        RedisStandaloneConfiguration standaloneConfig =
                new RedisStandaloneConfiguration(masterInfo.host(), masterInfo.port());
        if (masterInfo.username() != null && !masterInfo.username().isBlank()) {
            standaloneConfig.setUsername(masterInfo.username());
        }
        if (passwordEfectivo != null && !passwordEfectivo.isBlank()) {
            standaloneConfig.setPassword(RedisPassword.of(passwordEfectivo));
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .clientOptions(clientOptions);
        if (masterInfo.ssl()) {
            builder.useSsl();
        }

        return new LettuceConnectionFactory(standaloneConfig, builder.build());
    }

    @Bean
    public ApplicationRunner inicializarSuscripcionRedisPubSub(
            RedisConnectionFactory connectionFactory,
            RedisService redisService,
            @Value("${app.redis.external-enabled:true}") boolean externalEnabled,
            @Value("${spring.data.redis.sentinel.master:educontrol-master}") String sentinelMaster,
            @Value("${spring.data.redis.sentinel.nodes:}") String sentinelNodes,
            @Value("${app.redis.replica-urls:}") String replicaUrls) {
        return args -> {
            if (!externalEnabled) {
                return;
            }
            String modoTopologia = !parsearNodos(sentinelNodes).isEmpty()
                    ? "SENTINEL + MASTER-REPLICA (Master: " + sentinelMaster + ", ReadFrom: REPLICA_PREFERRED)"
                    : (!parsearListaUris(replicaUrls).isEmpty()
                            ? "MASTER-REPLICA (ReadFrom: REPLICA_PREFERRED)"
                            : "STANDALONE (Sin réplicas configuradas)");

            try (RedisConnection conn = connectionFactory.getConnection()) {
                String pong = conn.ping();
                if (pong != null) {
                    String detalleReplicacion = obtenerResumenReplicacion(conn);
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
                    System.out.println(">>> Redis conectado exitosamente | Topología: " + modoTopologia
                            + detalleReplicacion
                            + " | (Cache + Sesiones + Pub/Sub + Candados activos)");
                }
            } catch (Exception e) {
                System.out.println(">>> Redis externo no detectado en este entorno [" + modoTopologia + "] ("
                        + e.getMessage() + "). Usando pizarra en memoria local como respaldo.");
            }
        };
    }

    private String obtenerResumenReplicacion(RedisConnection conn) {
        try {
            Properties info = conn.serverCommands().info("replication");
            if (info != null) {
                String role = info.getProperty("role", "desconocido");
                String replicas = info.getProperty("connected_slaves", "0");
                return " | Rol: " + role + " | Réplicas conectadas: " + replicas;
            }
        } catch (Exception ignored) {
        }
        return "";
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

    public record EndpointInfo(String host, int port, String username, String password, boolean ssl) {}

    public static EndpointInfo parsearRedisUri(String rawUri, String defaultHost, int defaultPort) {
        if (rawUri == null || rawUri.isBlank()) {
            return new EndpointInfo(defaultHost, defaultPort, null, null, false);
        }
        String limpio = rawUri.trim();
        try {
            if (!limpio.contains("://")) {
                limpio = "redis://" + limpio;
            }
            URI uri = URI.create(limpio);
            String host = uri.getHost() != null ? uri.getHost() : defaultHost;
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            boolean ssl = "rediss".equalsIgnoreCase(uri.getScheme());
            String username = null;
            String password = null;
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                int idx = userInfo.indexOf(':');
                if (idx >= 0) {
                    String u = userInfo.substring(0, idx);
                    if (!u.isBlank() && !"default".equalsIgnoreCase(u)) {
                        username = u;
                    }
                    password = userInfo.substring(idx + 1);
                } else {
                    password = userInfo;
                }
            }
            return new EndpointInfo(host, port, username, password, ssl);
        } catch (Exception e) {
            return new EndpointInfo(defaultHost, defaultPort, null, null, false);
        }
    }

    public static List<RedisNode> parsearNodos(String rawNodes) {
        List<RedisNode> resultado = new ArrayList<>();
        if (rawNodes == null || rawNodes.isBlank()) {
            return resultado;
        }
        for (String token : rawNodes.split(",")) {
            String item = token.trim();
            if (item.isEmpty()) {
                continue;
            }
            EndpointInfo info = parsearRedisUri(item, null, 26379);
            if (info.host() != null && !info.host().isBlank()) {
                resultado.add(new RedisNode(info.host(), info.port()));
            }
        }
        return resultado;
    }

    public static List<EndpointInfo> parsearListaUris(String rawUris) {
        List<EndpointInfo> resultado = new ArrayList<>();
        if (rawUris == null || rawUris.isBlank()) {
            return resultado;
        }
        for (String token : rawUris.split(",")) {
            String item = token.trim();
            if (item.isEmpty()) {
                continue;
            }
            EndpointInfo info = parsearRedisUri(item, null, 6379);
            if (info.host() != null && !info.host().isBlank()) {
                resultado.add(info);
            }
        }
        return resultado;
    }
}

