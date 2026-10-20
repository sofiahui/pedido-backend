package cl.duocuc.pedidos360.notify.messaging.consumer.kitchen;

import cl.duocuc.pedidos360.notify.messaging.CommandEnvelope;
import cl.duocuc.pedidos360.notify.messaging.consumer.BaseCommandConsumer;
import cl.duocuc.pedidos360.notify.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Consumidor del flujo KITCHEN: imprime el ticket de cocina cuando el pedido es aceptado. */
@Component
public class KitchenCommandConsumer extends BaseCommandConsumer {

    private final NotificationService notificationService;

    public KitchenCommandConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        super(objectMapper);
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${app.rabbit.kitchen.queue}")
    public void onMessage(Message message, Channel channel) throws IOException {
        handle(message, channel, "KitchenTicket");
    }

    @Override
    protected void process(CommandEnvelope command) {
        notificationService.printKitchenTicket(command);
    }
}