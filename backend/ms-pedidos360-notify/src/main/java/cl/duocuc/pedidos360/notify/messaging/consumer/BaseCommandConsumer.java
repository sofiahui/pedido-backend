package cl.duocuc.pedidos360.notify.messaging.consumer;

import cl.duocuc.pedidos360.notify.exception.NonRecoverableCommandException;
import cl.duocuc.pedidos360.notify.messaging.CommandEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Política común de confirmación para todos los consumidores (ACK manual):
 *
 * 1. Mensaje inválido (JSON roto, tipo incorrecto, datos faltantes)
 *    -> NACK sin reencolar -> DLQ. Reintentar no lo arreglaría.
 * 2. Mensaje duplicado (eventId ya procesado)
 *    -> ACK sin reprocesar (idempotencia).
 * 3. Procesado correctamente -> ACK.
 * 4. Error no recuperable (NonRecoverableCommandException) -> NACK sin reencolar -> DLQ.
 * 5. Error temporal (cualquier otra excepción):
 *    - primer intento -> NACK con reencolado (1 reintento);
 *    - si ya era un reintento -> NACK sin reencolar -> DLQ.
 *
 * Toda decisión queda registrada en el log.
 */
public abstract class BaseCommandConsumer {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    protected BaseCommandConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Lógica propia de cada consumidor. */
    protected abstract void process(CommandEnvelope command);

    protected void handle(Message message, Channel channel, String expectedType) throws IOException {
        MessageProperties mp = message.getMessageProperties();
        long tag = mp.getDeliveryTag();
        String queue = mp.getConsumerQueue();

        // 1. Leer y validar el sobre
        CommandEnvelope command;
        try {
            command = objectMapper.readValue(message.getBody(), CommandEnvelope.class);
            validate(command, expectedType);
        } catch (Exception e) {
            log.error("[{}] Mensaje inválido, se envía a DLQ: {}", queue, e.getMessage());
            channel.basicNack(tag, false, false);
            return;
        }

        // 2. Idempotencia
        if (processedEventIds.contains(command.eventId())) {
            log.warn("[{}] Comando duplicado (eventId={}), se confirma sin reprocesar", queue, command.eventId());
            channel.basicAck(tag, false);
            return;
        }

        try {
            // 3. Procesar y confirmar
            process(command);
            processedEventIds.add(command.eventId());
            channel.basicAck(tag, false);
            log.info("[{}] ACK {} (eventId={}, pedido={})",
                    queue, command.type(), command.eventId(), command.correlationId());

        } catch (NonRecoverableCommandException e) {
            // 4. No recuperable
            log.error("[{}] Error no recuperable en {} (eventId={}): {}. Se envía a DLQ",
                    queue, command.type(), command.eventId(), e.getMessage());
            channel.basicNack(tag, false, false);

        } catch (Exception e) {
            // 5. Temporal: un reintento y luego DLQ
            if (Boolean.TRUE.equals(mp.getRedelivered())) {
                log.error("[{}] Falló también el reintento de {} (eventId={}): {}. Se envía a DLQ",
                        queue, command.type(), command.eventId(), e.getMessage());
                channel.basicNack(tag, false, false);
            } else {
                log.warn("[{}] Error temporal en {} (eventId={}): {}. Se reintenta una vez",
                        queue, command.type(), command.eventId(), e.getMessage());
                channel.basicNack(tag, false, true);
            }
        }
    }

    private void validate(CommandEnvelope c, String expectedType) {
        if (c.eventId() == null || c.eventId().isBlank()) {
            throw new IllegalArgumentException("Falta eventId");
        }
        if (!expectedType.equals(c.type())) {
            throw new IllegalArgumentException("Tipo " + c.type() + " no corresponde a esta cola (se esperaba " + expectedType + ")");
        }
        if (c.payload() == null || c.payload().get("orderId") == null) {
            throw new IllegalArgumentException("Falta payload.orderId");
        }
    }
}