package cl.duocuc.pedidos360.notify.messaging;

import java.time.Instant;
import java.util.Map;

/** Sobre común de los comandos recibidos desde RabbitMQ (mismo formato que publica orders). */
public record CommandEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        Map<String, Object> payload) {
}