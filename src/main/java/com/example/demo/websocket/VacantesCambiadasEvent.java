package com.example.demo.websocket;

import com.example.demo.dto.VacanteMensaje;

import java.util.List;

/**
 * Evento interno de Spring que se publica DENTRO de la transaccion
 * (matricular, anular, trasladar, editar seccion). El NotificadorWebSocket
 * lo recibe solo si la transaccion hizo COMMIT.
 *
 * @param secciones las secciones cuyas vacantes cambiaron
 * @param actividad texto para el feed "Actividad en vivo" (puede ser null)
 */
public record VacantesCambiadasEvent(List<VacanteMensaje> secciones, String actividad) {
}
