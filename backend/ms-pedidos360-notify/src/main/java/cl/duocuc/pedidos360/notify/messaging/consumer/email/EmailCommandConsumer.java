package cl.duocuc.pedidos360.notify.messaging.consumer.email;

import cl.duocuc.pedidos360.notify.messaging.CommandEnvelope;
import cl.duocuc.pedidos360.notify.messaging.consumer.BaseCommandConsumer;
import cl.duocuc.pedidos360.notify.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Consumidor del flujo EMAIL: notifica al cliente cada cambio de estado del pedido. */
@Component
public class EmailCommandConsumer extends BaseCommandConsumer {

    private final NotificationService notificationService;

    public EmailCommandConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        super(objectMapper);
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${app.rabbit.email.queue}")
    public void onMessage(Message message, Channel channel) throws IOException {
        handle(message, channel, "EmailSend");
    }

    @Override
    protected void process(CommandEnvelope command) {
        notificationService.sendEmail(command);
    }
}