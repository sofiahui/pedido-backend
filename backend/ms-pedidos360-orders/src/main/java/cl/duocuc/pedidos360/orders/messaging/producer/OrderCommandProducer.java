package cl.duocuc.pedidos360.orders.messaging.producer;

import cl.duocuc.pedidos360.orders.messaging.CommandEnvelope;
import cl.duocuc.pedidos360.orders.messaging.config.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Productor de comandos de Pedidos360. Publica en cmd.direct usando la clave
 * de cada flujo. Si RabbitMQ falla, solo registra el error: el pedido no se ve afectado.
 */
@Component
public class OrderCommandProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public OrderCommandProducer(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    /** Notificación por email/push al cliente. */
    public void sendEmail(String orderId, Map<String, Object> payload) {
        publish(props.email(), "EmailSend", orderId, payload);
    }

    /** Ticket de cocina. */
    public void sendKitchenTicket(String orderId, Map<String, Object> payload) {
        publish(props.kitchen(), "KitchenTicket", orderId, payload);
    }

    /** Generación de boleta o factura. */
    public void sendInvoice(String orderId, Map<String, Object> payload) {
        publish(props.invoice(), "InvoiceGen", orderId, payload);
    }

    private void publish(MessagingProperties.Flow flow, String type,
                         String orderId, Map<String, Object> payload) {
        CommandEnvelope envelope = CommandEnvelope.of(type, orderId, payload);
        try {
            rabbitTemplate.convertAndSend(
                    props.exchanges().direct(),
                    flow.routingKey(),
                    envelope,
                    message -> {
                        message.getMessageProperties().setMessageId(envelope.eventId());
                        message.getMessageProperties().setCorrelationId(envelope.correlationId());
                        return message;
                    });
            log.info("Comando {} publicado (eventId={}, pedido={}, clave={})",
                    type, envelope.eventId(), orderId, flow.routingKey());
        } catch (Exception e) {
            log.error("No se pudo publicar el comando {} del pedido {}: {}",
                    type, orderId, e.getMessage());
        }
    }
}