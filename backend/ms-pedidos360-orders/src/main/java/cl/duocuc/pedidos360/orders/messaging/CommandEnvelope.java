package cl.duocuc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre común para todos los comandos que viajan por RabbitMQ.
 * type: qué tarea es (EmailSend, KitchenTicket, InvoiceGen).
 * correlationId: id del pedido, para seguirlo entre servicios.
 */
public record CommandEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        Map<String, Object> payload) {

    public static CommandEnvelope of(String type, String correlationId, Map<String, Object> payload) {
        return new CommandEnvelope(
                type,
                UUID.randomUUID().toString(),
                Instant.now(),
                UUID.randomUUID().toString(),
                correlationId,
                payload);
    }
}