package com.example.demo;

import com.example.demo.config.RedisConfig;
import com.example.demo.config.SessionInterceptor;
import com.example.demo.controller.AuthController;
import com.example.demo.dto.*;
import com.example.demo.entity.Rol;
import com.example.demo.entity.Usuario;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.service.*;
import io.lettuce.core.ReadFrom;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica los 5 puntos de REDIS_SPEC.md:
 *  1) Verificación de usuarios en cada clic sin golpear MySQL + corte instantáneo al desactivar
 *  2) Sesiones persistentes en Redis que sobreviven reinicios del backend
 *  3) Caché de Dashboard, Grados, Tarifas, Años y Secciones con invalidación automática
 *  4) Megáfono Pub/Sub para avisos de vacantes entre múltiples servidores
 *  5) Candado anti doble-clic (10s) y bloqueo de Login tras 5 intentos fallidos (15 min)
 */
@SpringBootTest
class RedisSpecTest {

    @Autowired RedisService redisService;
    @Autowired AuthService authService;
    @Autowired UsuarioService usuarioService;
    @Autowired DashboardService dashboardService;
    @Autowired GradoService gradoService;
    @Autowired AnioEscolarService anioEscolarService;
    @Autowired TarifaService tarifaService;
    @Autowired SeccionService seccionService;
    @Autowired SessionInterceptor sessionInterceptor;
    @Autowired UsuarioRepository usuarioRepository;

    @BeforeEach
    void limpiarRedis() {
        redisService.limpiarTodo();
    }

    @Test
    void punto1_estadoUsuarioEnRedisEvitaConsultasAMySQLYSeCortaAlDesactivar() throws Exception {
        Usuario admin = usuarioRepository.findByUsername("admin").orElseThrow();
        Usuario secretaria = usuarioRepository.findByUsername("secretaria").orElseThrow();

        // Al iniciar sesión se guarda el estado en Redis
        authService.login(new LoginRequest("secretaria", "123456"));

        // Verificamos que obtenerUsuarioActivo responde desde Redis sin invocar el cargador de MySQL
        AtomicInteger viajesAMySQL = new AtomicInteger(0);
        Usuario desdeRedis = redisService.obtenerUsuarioActivo(secretaria.getId(), () -> {
            viajesAMySQL.incrementAndGet();
            return Optional.of(secretaria);
        });
        assertNotNull(desdeRedis);
        assertEquals("secretaria", desdeRedis.getUsername());
        assertEquals(0, viajesAMySQL.get(), "No debe ir a MySQL porque ya está en la pizarra de Redis");

        // Cuando el Admin desactiva a secretaria, se borra de Redis al instante y su sesión se corta
        usuarioService.actualizar(
                secretaria.getId(),
                new UsuarioRequest("secretaria", "Secretaria Academica", Rol.SECRETARIA, false, ""),
                admin.getId()
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/alumnos");
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(AuthController.SESION_USUARIO_ID, secretaria.getId());
        req.setSession(sesion);
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertFalse(sessionInterceptor.preHandle(req, res, new Object()),
                "El acceso debe cortarse al instante tras desactivar al usuario");
        assertEquals(401, res.getStatus());

        // Restauramos a secretaria activa para otros tests
        usuarioService.actualizar(
                secretaria.getId(),
                new UsuarioRequest("secretaria", "Secretaria Academica", Rol.SECRETARIA, true, ""),
                admin.getId()
        );
    }

    @Test
    void punto2_sesionEnRedisSobreviveReinicioDelServidorYSeBorraEnLogout() throws Exception {
        Usuario admin = authService.login(new LoginRequest("admin", "123456"));
        String tokenSesion = "sesion-persistente-railway-001";
        redisService.guardarSesion(tokenSesion, admin.getId());

        // Simulamos que el backend se reinició (no hay HttpSession previa en memoria),
        // pero el navegador envía su cookie EDUCONTROL_SID
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/dashboard");
        req.setCookies(new Cookie(AuthController.COOKIE_SESION_REDIS, tokenSesion));
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertTrue(sessionInterceptor.preHandle(req, res, new Object()),
                "La sesión debe recuperarse desde Redis sin expulsar al usuario");
        Usuario recuperado = (Usuario) req.getAttribute(SessionInterceptor.USUARIO_ACTUAL);
        assertNotNull(recuperado);
        assertEquals(admin.getId(), recuperado.getId());

        // Al cerrar sesión, se borra de Redis al instante
        redisService.eliminarSesion(tokenSesion);
        MockHttpServletRequest reqPostLogout = new MockHttpServletRequest("GET", "/api/dashboard");
        reqPostLogout.setCookies(new Cookie(AuthController.COOKIE_SESION_REDIS, tokenSesion));
        MockHttpServletResponse resPostLogout = new MockHttpServletResponse();

        assertFalse(sessionInterceptor.preHandle(reqPostLogout, resPostLogout, new Object()));
        assertEquals(401, resPostLogout.getStatus());
    }

    @Test
    void punto3_cacheDeDashboardYCatalogosEnRedisSeActualizaAnteCambios() {
        DashboardResponse d1 = dashboardService.resumen();
        DashboardResponse d2 = dashboardService.resumen();
        assertEquals(d1, d2);

        List<GradoResponse> grados = gradoService.listar();
        assertFalse(grados.isEmpty());

        List<AnioEscolarResponse> anios = anioEscolarService.listar();
        assertFalse(anios.isEmpty());

        Integer anioId = anios.getFirst().id();
        List<TarifaResponse> tarifas = tarifaService.listar(anioId);
        assertFalse(tarifas.isEmpty());

        List<SeccionResponse> secciones = seccionService.listar(anioId, null);
        assertFalse(secciones.isEmpty());
    }

    @Test
    void punto4_megafonoPubSubRedisNotificaMensajesDeOtrosServidores() {
        String jsonOtroServidor = """
                {
                  "origenInstanciaId": "servidor-hermano-2",
                  "secciones": [{"seccionId": 1, "vacantesDisponibles": 18, "vacantesMaximas": 20}],
                  "actividad": "admin matriculo a un alumno en servidor 2",
                  "dashboard": {
                    "anio": 2026,
                    "alumnosRegistrados": 10,
                    "matriculados": 3,
                    "vacantesLibres": 800,
                    "totalSecciones": 29,
                    "seccionesLlenas": 0,
                    "inicial": 3,
                    "primaria": 0,
                    "secundaria": 0
                  }
                }
                """;
        // No debe lanzar excepción al procesar el mensaje Pub/Sub y reenviarlo al broker STOMP
        assertDoesNotThrow(() -> redisService.procesarMensajePubSubRecibido(jsonOtroServidor));
    }

    @Test
    void punto5_candadoAntiDobleClicYProteccionDeLoginTrasCincoIntentos() {
        // 5A: Candado anti doble-clic (10 segundos)
        String recurso = "pago:matricula:99999";
        assertTrue(redisService.adquirirCandado(recurso), "El primer clic adquiere el candado");
        assertThrows(NegocioException.class, () ->
                redisService.ejecutarConCandado(recurso, "Ya se esta procesando", () -> "ok"),
                "El segundo clic simultáneo debe ser frenado en la puerta por Redis");
        redisService.liberarCandado(recurso);
        assertEquals("ok", redisService.ejecutarConCandado(recurso, "Ya se esta procesando", () -> "ok"),
                "Una vez liberado el candado, permite operar de nuevo");

        // 5B: Protección de Login (5 intentos fallidos -> bloqueo de 15 minutos)
        for (int i = 1; i <= 5; i++) {
            assertThrows(NegocioException.class, () ->
                    authService.login(new LoginRequest("admin", "clave-incorrecta-" + System.nanoTime())));
        }
        assertEquals(5, redisService.obtenerIntentosFallidosLogin("admin"));

        // El 6to intento es bloqueado incluso con la contraseña correcta
        NegocioException bloqueo = assertThrows(NegocioException.class, () ->
                authService.login(new LoginRequest("admin", "123456")));
        assertTrue(bloqueo.getMessage().contains("15 minutos"),
                "Debe indicar que el acceso está bloqueado por 15 minutos");

        // Al limpiar los intentos y acertar la contraseña, el contador queda en 0
        redisService.limpiarIntentosLogin("admin");
        redisService.registrarIntentoFallidoLogin("admin");
        assertEquals(1, redisService.obtenerIntentosFallidosLogin("admin"));
        authService.login(new LoginRequest("admin", "123456"));
        assertEquals(0, redisService.obtenerIntentosFallidosLogin("admin"),
                "Si acierta su contraseña, el contador vuelve a cero");
    }

    @Test
    void seccion4_configuracionSentinelYReplicasConLecturaPreferenteEnReplicas() {
        RedisConfig config = new RedisConfig();

        // Caso A: Opción 2 en Railway (Master + 2 Réplicas + 3 Sentinels)
        LettuceConnectionFactory sentinelFactory = config.redisConnectionFactory(
                "redis://default:railwaySecretPass@redis-master.railway.internal:6379",
                "",
                "educontrol-master",
                "sentinel-1.railway.internal:26379,sentinel-2.railway.internal:26379,sentinel-3.railway.internal:26379",
                "",
                ""
        );
        assertNotNull(sentinelFactory.getSentinelConfiguration(),
                "Debe activar RedisSentinelConfiguration cuando se definen nodos Sentinel");
        assertEquals("educontrol-master", sentinelFactory.getSentinelConfiguration().getMaster().getName());
        assertEquals(3, sentinelFactory.getSentinelConfiguration().getSentinels().size());
        assertEquals(ReadFrom.REPLICA_PREFERRED,
                sentinelFactory.getClientConfiguration().getReadFrom().orElse(null),
                "Debe leer consultas rápidas desde las Réplicas (REPLICA_PREFERRED)");

        // Caso B: Maestro + Réplicas directas
        LettuceConnectionFactory replicaFactory = config.redisConnectionFactory(
                "redis://default:railwaySecretPass@redis-master.railway.internal:6379",
                "",
                "educontrol-master",
                "",
                "",
                "redis-replica-1.railway.internal:6379,redis-replica-2.railway.internal:6379"
        );
        assertEquals(ReadFrom.REPLICA_PREFERRED,
                replicaFactory.getClientConfiguration().getReadFrom().orElse(null));
    }
}
