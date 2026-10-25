package com.example.demo.websocket;

import com.example.demo.dto.DashboardResponse;
import com.example.demo.dto.MensajeResponse;
import com.example.demo.dto.VacanteMensaje;
import com.example.demo.service.DashboardService;
import com.example.demo.service.RedisService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Envia a TODOS los navegadores conectados los cambios de vacantes.
 *
 * Punto 3 y Punto 4 de REDIS_SPEC.md:
 *  - Al confirmarse el COMMIT en MySQL, invalida y actualiza el resumen del Dashboard
 *    y las vacantes por sección en Redis.
 *  - Publica el aviso en el "Megáfono" Pub/Sub de Redis (canal educontrol:canal:vacantes)
 *    para que si hay 2 o más servidores de EduControl funcionando a la vez, todos escuchen
 *    el aviso y actualicen las pantallas de todos los usuarios al instante.
 */
@Component
public class NotificadorWebSocket {

    public static final String TOPIC_VACANTES = "/topic/vacantes";
    public static final String TOPIC_ACTIVIDAD = "/topic/actividad";
    public static final String TOPIC_DASHBOARD = "/topic/dashboard";

    private final SimpMessagingTemplate mensajeria;
    private final DashboardService dashboardService;
    private final RedisService redisService;

    public NotificadorWebSocket(SimpMessagingTemplate mensajeria,
                                DashboardService dashboardService,
                                RedisService redisService) {
        this.mensajeria = mensajeria;
        this.dashboardService = dashboardService;
        this.redisService = redisService;
        this.redisService.registrarSuscriptorVacantes(this::alRecibirDeRedisPubSub);
    }

    // REQUIRES_NEW: la transaccion original ya termino; el resumen del
    // dashboard se lee en una transaccion nueva y limpia y se guarda en Redis.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void alConfirmarse(VacantesCambiadasEvent evento) {

        // 1) Invalida la caché anterior de secciones y dashboard en Redis y calcula la nueva cifra
        redisService.invalidarSeccionesYDashboard();
        DashboardResponse resumenActualizado = dashboardService.resumen();

        // 2) Envía a las pantallas conectadas a este servidor
        enviarAPantallasLocales(evento.secciones(), evento.actividad(), resumenActualizado);

        // 3) Avisa por el megáfono Pub/Sub de Redis a los demás servidores de EduControl
        redisService.publicarCambioVacantes(evento.secciones(), evento.actividad(), resumenActualizado);
    }

    private void alRecibirDeRedisPubSub(RedisService.MensajePubSubVacantes msg) {
        enviarAPantallasLocales(msg.secciones(), msg.actividad(), msg.dashboard());
    }

    private void enviarAPantallasLocales(List<VacanteMensaje> secciones,
                                         String actividad,
                                         DashboardResponse dashboard) {
        if (secciones != null) {
            mensajeria.convertAndSend(TOPIC_VACANTES, secciones);
        }
        if (actividad != null) {
            mensajeria.convertAndSend(TOPIC_ACTIVIDAD, new MensajeResponse(actividad));
        }
        if (dashboard != null) {
            mensajeria.convertAndSend(TOPIC_DASHBOARD, dashboard);
        }
    }
}
