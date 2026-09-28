package com.example.demo.websocket;

import com.example.demo.dto.MensajeResponse;
import com.example.demo.service.DashboardService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia a TODOS los navegadores conectados los cambios de vacantes.
 *
 * AFTER_COMMIT es clave: si avisaramos antes del commit y luego la
 * transaccion hiciera rollback, las pantallas mostrarian vacantes falsas.
 */
@Component
public class NotificadorWebSocket {

    public static final String TOPIC_VACANTES = "/topic/vacantes";
    public static final String TOPIC_ACTIVIDAD = "/topic/actividad";
    public static final String TOPIC_DASHBOARD = "/topic/dashboard";

    private final SimpMessagingTemplate mensajeria;
    private final DashboardService dashboardService;

    public NotificadorWebSocket(SimpMessagingTemplate mensajeria,
                                DashboardService dashboardService) {
        this.mensajeria = mensajeria;
        this.dashboardService = dashboardService;
    }

    // REQUIRES_NEW: la transaccion original ya termino; el resumen del
    // dashboard se lee en una transaccion nueva y limpia.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void alConfirmarse(VacantesCambiadasEvent evento) {

        mensajeria.convertAndSend(TOPIC_VACANTES, evento.secciones());

        if (evento.actividad() != null) {
            mensajeria.convertAndSend(TOPIC_ACTIVIDAD, new MensajeResponse(evento.actividad()));
        }

        mensajeria.convertAndSend(TOPIC_DASHBOARD, dashboardService.resumen());
    }
}
