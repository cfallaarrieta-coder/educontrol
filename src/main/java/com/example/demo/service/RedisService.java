package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.Usuario;
import com.example.demo.exception.NegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Servicio central de Redis para EduControl (implementación de REDIS_SPEC.md).
 *
 * Cubre los 5 puntos clave y la tabla de tiempos de vida (TTL):
 *  1) Estado del usuario en cada clic (SessionInterceptor)          -> TTL: 15 minutos
 *  2) Sesiones persistentes que no se pierden (AuthController)      -> TTL: 30 minutos de inactividad
 *  3) Caché de Dashboard (10 min), Catálogos (12 h) y Secciones (30 min)
 *  4) Megáfono Pub/Sub de vacantes en tiempo real (NotificadorWebSocket)
 *  5) Candado anti doble-clic (10 seg) y protección anti fuerza bruta en Login (5 intentos / 15 min)
 *
 * Incluye tolerancia a fallos en cliente: si el servidor de Redis externo no responde temporalmente
 * o se ejecutan pruebas locales sin daemon de Redis, degrada de forma transparente a una pizarra
 * en memoria con los mismos TTLs para que EduControl nunca se detenga.
 */
@Service
public class RedisService {

    // =========================================================================
    // Tiempos de expiración (TTL) definidos en la Sección 5 de REDIS_SPEC.md
    // =========================================================================
    public static final Duration TTL_SESION           = Duration.ofMinutes(30);
    public static final Duration TTL_ESTADO_USUARIO   = Duration.ofMinutes(15);
    public static final Duration TTL_INTENTOS_LOGIN   = Duration.ofMinutes(15);
    public static final Duration TTL_DASHBOARD        = Duration.ofMinutes(10);
    public static final Duration TTL_CATALOGOS        = Duration.ofHours(12);
    public static final Duration TTL_VACANTES_SECCION = Duration.ofMinutes(30);
    public static final Duration TTL_CANDADO          = Duration.ofSeconds(10);

    public static final int MAX_INTENTOS_LOGIN = 5;

    // =========================================================================
    // Prefijos de llaves en Redis (visibles en la pestaña Data de Railway)
    // =========================================================================
    public static final String KEY_SESION_PREFIX          = "educontrol:sesion:";
    public static final String KEY_USUARIO_SESIONES       = "educontrol:usuario:sesiones:";
    public static final String KEY_USUARIO_ESTADO_PREFIX  = "educontrol:usuario:estado:";
    public static final String KEY_LOGIN_INTENTOS_PREFIX  = "educontrol:login:intentos:";
    public static final String KEY_DASHBOARD_RESUMEN      = "educontrol:dashboard:resumen";
    public static final String KEY_CATALOGO_GRADOS        = "educontrol:catalogo:grados";
    public static final String KEY_CATALOGO_ANIOS         = "educontrol:catalogo:anios";
    public static final String KEY_CATALOGO_TARIFAS_PREF  = "educontrol:catalogo:tarifas:";
    public static final String KEY_SECCIONES_LISTA_PREF   = "educontrol:secciones:lista:";
    public static final String KEY_SECCIONES_ID_PREF      = "educontrol:secciones:id:";
    public static final String KEY_LOCK_PREFIX            = "educontrol:lock:";

    /** Canal Pub/Sub ("Megáfono") para avisos de vacantes en tiempo real (Punto 4). */
    public static final String CANAL_VACANTES = "educontrol:canal:vacantes";

    /** Identificador único de esta instancia del servidor (para Pub/Sub multi-servidor). */
    private final String instanciaId = UUID.randomUUID().toString();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean externalEnabled;

    /** Circuit-breaker ligero: si Redis cae, evita bloquear peticiones durante 15 segundos. */
    private final AtomicLong reintentarRedisExternoDespuesDe = new AtomicLong(0);

    /** Respaldo local con TTL para desarrollo sin Redis o caída temporal del nodo. */
    private final ConcurrentHashMap<String, EntradaLocal> respaldoLocal = new ConcurrentHashMap<>();

    /** Suscriptor local del megáfono Pub/Sub. */
    private volatile Consumer<MensajePubSubVacantes> suscriptorPubSub;

    private record EntradaLocal(String valor, long expiraEnMs) {
        boolean expirada() {
            return System.currentTimeMillis() > expiraEnMs;
        }
    }

    /** Mensaje que viaja por el canal Redis Pub/Sub cuando cambia una vacante. */
    public record MensajePubSubVacantes(
            String origenInstanciaId,
            List<VacanteMensaje> secciones,
            String actividad,
            DashboardResponse dashboard
    ) {}

    public RedisService(StringRedisTemplate redisTemplate,
                        ObjectMapper objectMapper,
                        @Value("${app.redis.external-enabled:true}") boolean externalEnabled) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.externalEnabled = externalEnabled;
    }

    public String getInstanciaId() {
        return instanciaId;
    }

    // =========================================================================
    // PUNTO 1: Verificación de usuarios en cada clic (SessionInterceptor)
    //          TTL: 15 minutos
    // =========================================================================

    /**
     * Guarda el estado actual del usuario en Redis (TTL 15 minutos).
     * Si el usuario no está activo, borra su estado y sus sesiones.
     */
    public void guardarEstadoUsuario(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return;
        }
        if (!usuario.estaActivo()) {
            invalidarUsuario(usuario.getId());
            return;
        }
        UsuarioResponse dto = UsuarioResponse.desde(usuario);
        escribirJson(KEY_USUARIO_ESTADO_PREFIX + usuario.getId(), dto, TTL_ESTADO_USUARIO);
    }

    /**
     * Obtiene el usuario desde Redis en menos de 1ms sin consultar MySQL.
     * Si no está en Redis, lo busca con el supplier de MySQL y lo anota en la pizarra.
     */
    public Usuario obtenerUsuarioActivo(Integer usuarioId, Supplier<Optional<Usuario>> cargadorMySQL) {
        if (usuarioId == null) {
            return null;
        }
        String key = KEY_USUARIO_ESTADO_PREFIX + usuarioId;
        UsuarioResponse enCache = leerJson(key, UsuarioResponse.class);
        if (enCache != null) {
            if (!enCache.activo()) {
                return null;
            }
            Usuario u = new Usuario(enCache.username(), "", enCache.nombreCompleto(), enCache.rol());
            u.setId(enCache.id());
            u.setActivo(true);
            return u;
        }

        Optional<Usuario> desdeBd = cargadorMySQL.get();
        if (desdeBd.isPresent() && desdeBd.get().estaActivo()) {
            guardarEstadoUsuario(desdeBd.get());
            return desdeBd.get();
        }
        return null;
    }

    /**
     * Cuando el Admin edita, desactiva o elimina a un usuario (o cambia su contraseña),
     * se borra inmediatamente de la pizarra de Redis para que el cambio aplique al instante.
     */
    public void invalidarEstadoUsuario(Integer usuarioId) {
        if (usuarioId == null) {
            return;
        }
        borrarClave(KEY_USUARIO_ESTADO_PREFIX + usuarioId);
    }

    /**
     * Invalida tanto el estado cacheado del usuario como todas sus sesiones activas en Redis.
     */
    public void invalidarUsuario(Integer usuarioId) {
        if (usuarioId == null) {
            return;
        }
        invalidarEstadoUsuario(usuarioId);
        invalidarSesionesDeUsuario(usuarioId);
    }

    // =========================================================================
    // PUNTO 2: Sesiones que no se pierden (AuthController)
    //          TTL: 30 minutos de inactividad
    // =========================================================================

    /**
     * Registra una sesión activa en Redis con vencimiento de 30 minutos de inactividad.
     */
    public void guardarSesion(String sessionId, Integer usuarioId) {
        if (sessionId == null || sessionId.isBlank() || usuarioId == null) {
            return;
        }
        escribirTexto(KEY_SESION_PREFIX + sessionId, String.valueOf(usuarioId), TTL_SESION);
        registrarSesionEnIndiceUsuario(usuarioId, sessionId);
    }

    /**
     * Consulta a qué usuario pertenece la sesión en Redis y renueva los 30 minutos de inactividad.
     */
    public Integer obtenerYRenovarSesion(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        String key = KEY_SESION_PREFIX + sessionId;
        String valor = leerTexto(key);
        if (valor == null) {
            return null;
        }
        try {
            Integer usuarioId = Integer.valueOf(valor.trim());
            // Renueva el TTL a 30 minutos tras actividad del usuario
            escribirTexto(key, String.valueOf(usuarioId), TTL_SESION);
            return usuarioId;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Borra la sesión en Redis al instante cuando el usuario cierra sesión o cambia su clave.
     */
    public void eliminarSesion(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        borrarClave(KEY_SESION_PREFIX + sessionId);
    }

    /**
     * Corta todas las sesiones abiertas de un usuario (al cambiar contraseña o ser desactivado).
     */
    public void invalidarSesionesDeUsuario(Integer usuarioId) {
        if (usuarioId == null) {
            return;
        }
        String keyIndice = KEY_USUARIO_SESIONES + usuarioId;
        List<String> sesiones = leerJson(keyIndice, new TypeReference<List<String>>() {});
        if (sesiones != null) {
            for (String sid : sesiones) {
                borrarClave(KEY_SESION_PREFIX + sid);
            }
        }
        borrarClave(keyIndice);
    }

    private void registrarSesionEnIndiceUsuario(Integer usuarioId, String sessionId) {
        String keyIndice = KEY_USUARIO_SESIONES + usuarioId;
        List<String> actual = leerJson(keyIndice, new TypeReference<List<String>>() {});
        Set<String> conjunto = new LinkedHashSet<>();
        if (actual != null) {
            conjunto.addAll(actual);
        }
        conjunto.add(sessionId);
        escribirJson(keyIndice, new ArrayList<>(conjunto), TTL_SESION);
    }

    // =========================================================================
    // PUNTO 3: Carga instantánea del Dashboard (10 min), Catálogos (12 h)
    //          y Vacantes por Sección (30 min)
    // =========================================================================

    public DashboardResponse obtenerOCalcularDashboard(Supplier<DashboardResponse> calculador) {
        DashboardResponse enCache = leerJson(KEY_DASHBOARD_RESUMEN, DashboardResponse.class);
        if (enCache != null) {
            return enCache;
        }
        DashboardResponse calculado = calculador.get();
        if (calculado != null) {
            escribirJson(KEY_DASHBOARD_RESUMEN, calculado, TTL_DASHBOARD);
        }
        return calculado;
    }

    public void invalidarDashboard() {
        borrarClave(KEY_DASHBOARD_RESUMEN);
    }

    public List<GradoResponse> obtenerOCalcularGrados(Supplier<List<GradoResponse>> calculador) {
        List<GradoResponse> enCache = leerJson(KEY_CATALOGO_GRADOS, new TypeReference<List<GradoResponse>>() {});
        if (enCache != null) {
            return enCache;
        }
        List<GradoResponse> calculado = calculador.get();
        escribirJson(KEY_CATALOGO_GRADOS, calculado, TTL_CATALOGOS);
        return calculado;
    }

    public void invalidarGrados() {
        borrarClave(KEY_CATALOGO_GRADOS);
        invalidarSecciones();
    }

    public List<AnioEscolarResponse> obtenerOCalcularAnios(Supplier<List<AnioEscolarResponse>> calculador) {
        List<AnioEscolarResponse> enCache = leerJson(KEY_CATALOGO_ANIOS, new TypeReference<List<AnioEscolarResponse>>() {});
        if (enCache != null) {
            return enCache;
        }
        List<AnioEscolarResponse> calculado = calculador.get();
        escribirJson(KEY_CATALOGO_ANIOS, calculado, TTL_CATALOGOS);
        return calculado;
    }

    public void invalidarAnios() {
        borrarClave(KEY_CATALOGO_ANIOS);
        invalidarDashboard();
        invalidarSecciones();
        invalidarTarifasTodas();
    }

    public List<TarifaResponse> obtenerOCalcularTarifas(Integer anioId, Supplier<List<TarifaResponse>> calculador) {
        String key = KEY_CATALOGO_TARIFAS_PREF + (anioId == null ? "todos" : anioId);
        List<TarifaResponse> enCache = leerJson(key, new TypeReference<List<TarifaResponse>>() {});
        if (enCache != null) {
            return enCache;
        }
        List<TarifaResponse> calculado = calculador.get();
        escribirJson(key, calculado, TTL_CATALOGOS);
        return calculado;
    }

    public void invalidarTarifas(Integer anioId) {
        if (anioId != null) {
            borrarClave(KEY_CATALOGO_TARIFAS_PREF + anioId);
        }
        borrarPorPrefijo(KEY_CATALOGO_TARIFAS_PREF);
    }

    public void invalidarTarifasTodas() {
        borrarPorPrefijo(KEY_CATALOGO_TARIFAS_PREF);
    }

    public List<SeccionResponse> obtenerOCalcularSecciones(Integer anioId, Integer gradoId,
                                                           Supplier<List<SeccionResponse>> calculador) {
        String key = KEY_SECCIONES_LISTA_PREF
                + (anioId == null ? "todos" : anioId) + ":"
                + (gradoId == null ? "todos" : gradoId);
        List<SeccionResponse> enCache = leerJson(key, new TypeReference<List<SeccionResponse>>() {});
        if (enCache != null) {
            return enCache;
        }
        List<SeccionResponse> calculado = calculador.get();
        escribirJson(key, calculado, TTL_VACANTES_SECCION);
        return calculado;
    }

    public SeccionResponse obtenerOCalcularSeccionPorId(Integer seccionId, Supplier<SeccionResponse> calculador) {
        String key = KEY_SECCIONES_ID_PREF + seccionId;
        SeccionResponse enCache = leerJson(key, SeccionResponse.class);
        if (enCache != null) {
            return enCache;
        }
        SeccionResponse calculado = calculador.get();
        if (calculado != null) {
            escribirJson(key, calculado, TTL_VACANTES_SECCION);
        }
        return calculado;
    }

    public void invalidarSecciones() {
        borrarPorPrefijo(KEY_SECCIONES_LISTA_PREF);
        borrarPorPrefijo(KEY_SECCIONES_ID_PREF);
    }

    public void invalidarSeccionesYDashboard() {
        invalidarSecciones();
        invalidarDashboard();
    }

    // =========================================================================
    // PUNTO 4: Avisos de vacantes en tiempo real con Redis Pub/Sub ("Megáfono")
    // =========================================================================

    public void registrarSuscriptorVacantes(Consumer<MensajePubSubVacantes> suscriptor) {
        this.suscriptorPubSub = suscriptor;
    }

    /**
     * Publica el cambio de vacantes en el canal Redis Pub/Sub para que cualquier otro
     * servidor de EduControl conectado actualice las pantallas de sus usuarios al instante.
     */
    public void publicarCambioVacantes(List<VacanteMensaje> secciones,
                                       String actividad,
                                       DashboardResponse dashboard) {
        MensajePubSubVacantes msg = new MensajePubSubVacantes(instanciaId, secciones, actividad, dashboard);
        if (!debeUsarRedisExterno()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.convertAndSend(CANAL_VACANTES, json);
        } catch (Exception e) {
            marcarFalloTemporalRedis();
        }
    }

    /**
     * Invocado por RedisMessageListenerContainer cuando llega un mensaje por el canal Pub/Sub.
     */
    public void procesarMensajePubSubRecibido(String json) {
        Consumer<MensajePubSubVacantes> receptor = this.suscriptorPubSub;
        if (receptor == null || json == null || json.isBlank()) {
            return;
        }
        try {
            MensajePubSubVacantes msg = objectMapper.readValue(json, MensajePubSubVacantes.class);
            // Invalidamos caché local de esta instancia y notificamos si provino de otro servidor
            respaldoLocal.keySet().removeIf(k ->
                    k.startsWith(KEY_SECCIONES_LISTA_PREF)
                    || k.startsWith(KEY_SECCIONES_ID_PREF)
                    || k.equals(KEY_DASHBOARD_RESUMEN));
            if (msg != null && !instanciaId.equals(msg.origenInstanciaId())) {
                receptor.accept(msg);
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // PUNTO 5A: Candado anti doble-clic en Pagos y Matrículas (TTL: 10 segundos)
    // =========================================================================

    /**
     * Ejecuta una operación protegida por un candado atómico en Redis de 10 segundos.
     * Si entra un segundo clic mientras el primero sigue procesándose, lanza NegocioException
     * de inmediato sin sobrecargar a MySQL. Al terminar, libera el candado en el bloque finally.
     */
    public <T> T ejecutarConCandado(String nombreRecurso, String mensajeSiOcupado, Supplier<T> operacion) {
        if (!adquirirCandado(nombreRecurso)) {
            throw new NegocioException(mensajeSiOcupado);
        }
        try {
            return operacion.get();
        } finally {
            liberarCandado(nombreRecurso);
        }
    }

    public boolean adquirirCandado(String nombreRecurso) {
        String key = KEY_LOCK_PREFIX + nombreRecurso;
        if (debeUsarRedisExterno()) {
            try {
                Boolean adquirido = redisTemplate.opsForValue()
                        .setIfAbsent(key, "PROCESANDO", TTL_CANDADO);
                return Boolean.TRUE.equals(adquirido);
            } catch (Exception e) {
                marcarFalloTemporalRedis();
            }
        }
        long ahora = System.currentTimeMillis();
        long expira = ahora + TTL_CANDADO.toMillis();
        final boolean[] exito = {false};
        respaldoLocal.compute(key, (k, actual) -> {
            if (actual == null || actual.expirada()) {
                exito[0] = true;
                return new EntradaLocal("PROCESANDO", expira);
            }
            exito[0] = false;
            return actual;
        });
        return exito[0];
    }

    public void liberarCandado(String nombreRecurso) {
        borrarClave(KEY_LOCK_PREFIX + nombreRecurso);
    }

    // =========================================================================
    // PUNTO 5B: Protección de Login (5 intentos fallidos -> bloqueo de 15 min)
    // =========================================================================

    public void verificarLoginNoBloqueado(String username) {
        int intentos = obtenerIntentosFallidosLogin(username);
        if (intentos >= MAX_INTENTOS_LOGIN) {
            throw new NegocioException(
                    "Demasiados intentos fallidos (" + MAX_INTENTOS_LOGIN + "). "
                    + "Por seguridad, espera 15 minutos antes de volver a intentarlo.");
        }
    }

    public int obtenerIntentosFallidosLogin(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        String key = KEY_LOGIN_INTENTOS_PREFIX + normalizarUsuario(username);
        String valor = leerTexto(key);
        if (valor == null) {
            return 0;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int registrarIntentoFallidoLogin(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        String key = KEY_LOGIN_INTENTOS_PREFIX + normalizarUsuario(username);
        if (debeUsarRedisExterno()) {
            try {
                Long nuevoValor = redisTemplate.opsForValue().increment(key);
                if (nuevoValor != null) {
                    redisTemplate.expire(key, TTL_INTENTOS_LOGIN);
                    guardarEnRespaldoLocal(key, String.valueOf(nuevoValor), TTL_INTENTOS_LOGIN);
                    return nuevoValor.intValue();
                }
            } catch (Exception e) {
                marcarFalloTemporalRedis();
            }
        }
        long expira = System.currentTimeMillis() + TTL_INTENTOS_LOGIN.toMillis();
        EntradaLocal entrada = respaldoLocal.compute(key, (k, actual) -> {
            int previo = 0;
            if (actual != null && !actual.expirada()) {
                try {
                    previo = Integer.parseInt(actual.valor());
                } catch (NumberFormatException ignored) {
                }
            }
            return new EntradaLocal(String.valueOf(previo + 1), expira);
        });
        return Integer.parseInt(entrada.valor());
    }

    public void limpiarIntentosLogin(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        borrarClave(KEY_LOGIN_INTENTOS_PREFIX + normalizarUsuario(username));
    }

    private static String normalizarUsuario(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    // =========================================================================
    // Operaciones base de lectura/escritura con degradación elegante
    // =========================================================================

    private boolean debeUsarRedisExterno() {
        return externalEnabled && System.currentTimeMillis() >= reintentarRedisExternoDespuesDe.get();
    }

    private void marcarFalloTemporalRedis() {
        reintentarRedisExternoDespuesDe.set(System.currentTimeMillis() + 15_000L);
    }

    private void escribirTexto(String key, String valor, Duration ttl) {
        guardarEnRespaldoLocal(key, valor, ttl);
        if (debeUsarRedisExterno()) {
            try {
                redisTemplate.opsForValue().set(key, valor, ttl);
            } catch (Exception e) {
                marcarFalloTemporalRedis();
            }
        }
    }

    private String leerTexto(String key) {
        if (debeUsarRedisExterno()) {
            try {
                String valor = redisTemplate.opsForValue().get(key);
                if (valor != null) {
                    return valor;
                }
            } catch (Exception e) {
                marcarFalloTemporalRedis();
            }
        }
        return leerDeRespaldoLocal(key);
    }

    private <T> void escribirJson(String key, T objeto, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(objeto);
            escribirTexto(key, json, ttl);
        } catch (Exception ignored) {
        }
    }

    private <T> T leerJson(String key, Class<T> tipo) {
        String json = leerTexto(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, tipo);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T leerJson(String key, TypeReference<T> tipoRef) {
        String json = leerTexto(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, tipoRef);
        } catch (Exception e) {
            return null;
        }
    }

    public void borrarClave(String key) {
        respaldoLocal.remove(key);
        if (debeUsarRedisExterno()) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                marcarFalloTemporalRedis();
            }
        }
    }

    public void borrarPorPrefijo(String prefijo) {
        respaldoLocal.keySet().removeIf(k -> k.startsWith(prefijo));
        if (debeUsarRedisExterno()) {
            try {
                Set<String> keys = redisTemplate.keys(prefijo + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                marcarFalloTemporalRedis();
            }
        }
    }

    /** Limpia toda la pizarra local (útil entre tests). */
    public void limpiarTodo() {
        respaldoLocal.clear();
        if (debeUsarRedisExterno()) {
            try {
                Set<String> keys = redisTemplate.keys("educontrol:*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                marcarFalloTemporalRedis();
            }
        }
    }

    private void guardarEnRespaldoLocal(String key, String valor, Duration ttl) {
        long expira = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(Math.max(1, ttl.toSeconds()));
        respaldoLocal.put(key, new EntradaLocal(valor, expira));
    }

    private String leerDeRespaldoLocal(String key) {
        EntradaLocal entrada = respaldoLocal.get(key);
        if (entrada == null) {
            return null;
        }
        if (entrada.expirada()) {
            respaldoLocal.remove(key, entrada);
            return null;
        }
        return entrada.valor();
    }
}
