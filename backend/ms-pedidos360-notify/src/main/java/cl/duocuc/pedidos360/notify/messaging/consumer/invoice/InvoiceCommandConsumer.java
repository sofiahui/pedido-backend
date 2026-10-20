package cl.duocuc.pedidos360.notify.messaging.consumer.invoice;

import cl.duocuc.pedidos360.notify.messaging.CommandEnvelope;
import cl.duocuc.pedidos360.notify.messaging.consumer.BaseCommandConsumer;
import cl.duocuc.pedidos360.notify.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Consumidor del flujo INVOICE: genera la boleta cuando el pedido es entregado. */
@Component
public class InvoiceCommandConsumer extends BaseCommandConsumer {

    private final NotificationService notificationService;

    public InvoiceCommandConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        super(objectMapper);
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${app.rabbit.invoice.queue}")
    public void onMessage(Message message, Channel channel) throws IOException {
        handle(message, channel, "InvoiceGen");
    }

    @Override
    protected void process(CommandEnvelope command) {
        notificationService.generateInvoice(command);
    }
}