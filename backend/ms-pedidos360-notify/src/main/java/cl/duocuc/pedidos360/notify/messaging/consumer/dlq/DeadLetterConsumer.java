package cl.duocuc.pedidos360.notify.messaging.consumer.dlq;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Escucha las 3 DLQ (email, kitchen, invoice). Por cada mensaje no entregado deja en el log
 * el payload, la cola de origen y el motivo (cabecera x-death que agrega RabbitMQ) y luego
 * confirma con ACK para que la DLQ no se llene.
 *
 * Si se quiere conservar los mensajes en la DLQ para revisarlos a mano en el panel de
 * RabbitMQ, basta con poner app.rabbit.dlq-listener.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.rabbit.dlq-listener.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);
    private static final int MAX_PAYLOAD_CHARS = 1000;

    @RabbitListener(queues = {
            "${app.rabbit.email.dlq}",
            "${app.rabbit.kitchen.dlq}",
            "${app.rabbit.invoice.dlq}"})
    public void onDeadLetter(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            log.error("[DLQ] Mensaje no entregado: {}", describe(message));
        } catch (Exception e) {
            log.error("[DLQ] Mensaje no entregado (no se pudo leer su detalle: {})", e.getMessage());
        }
        channel.basicAck(tag, false);
    }

    /** Resume el mensaje: DLQ donde cayó, cola de origen, motivo, intentos, eventId y payload. */
    static String describe(Message message) {
        MessageProperties props = message.getMessageProperties();
        List<Map<String, ?>> deaths = props.getXDeathHeader();
        Map<String, ?> firstDeath = (deaths == null || deaths.isEmpty()) ? Map.of() : deaths.get(0);

        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        if (payload.length() > MAX_PAYLOAD_CHARS) {
            payload = payload.substring(0, MAX_PAYLOAD_CHARS) + "...(truncado)";
        }

        return "dlq=" + props.getConsumerQueue()
                + ", colaOrigen=" + valueOrUnknown(firstDeath.get("queue"))
                + ", motivo=" + valueOrUnknown(firstDeath.get("reason"))
                + ", veces=" + valueOrUnknown(firstDeath.get("count"))
                + ", messageId=" + valueOrUnknown(props.getMessageId())
                + ", payload=" + payload;
    }

    private static Object valueOrUnknown(Object value) {
        return value == null ? "desconocido" : value;
    }
}
