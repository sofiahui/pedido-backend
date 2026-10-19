package com.example;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Consumidor (Receiver) - Recibe mensajes de la cola 'hello'
 *
 * Basado en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Component
public class Receiver {

    /**
     * Este método se ejecuta automáticamente cada vez que
     * llega un mensaje a la cola 'hello'
     *
     * La anotación @RabbitListener:
     * - Registra este método como listener de mensajes
     * - queues="hello" especifica la cola a escuchar
     * - Spring AMQP maneja automáticamente:
     *   - Conexión a RabbitMQ
     *   - Deserialización del mensaje
     *   - Confirmación (ack) del mensaje
     *   - Manejo de excepciones
     *
     * Referencia:
     * https://docs.spring.io/spring-amqp/docs/current/api/org/springframework/amqp/rabbit/annotation/RabbitListener.html
     */
    @RabbitListener(queues = "hello")
    public void receiveMessage(String message) {
        try {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));

            System.out.println(
                    "[" + timestamp + "] [✓] Mensaje recibido: '" + message + "'"
            );

            // Aquí va tu lógica de procesamiento del mensaje
            // Si lanzas una excepción, Spring AMQP reintentará el mensaje
            // según la configuración de retry

        } catch (Exception e) {
            System.err.println("[✗] Error procesando mensaje: " + e.getMessage());
            e.printStackTrace();
            // Relanzar la excepción para que Spring AMQP maneje el retry
            throw new RuntimeException(e);
        }
    }

    /**
     * Método alternativo que recibe el mensaje como un objeto más complejo
     * (Este es un ejemplo avanzado)
     */
    @RabbitListener(queues = "hello")
    public void receiveMessageAdvanced(String message,
                                       Message rawMessage,
                                       Channel channel)
            throws Exception {
        try {
            System.out.println("[✓] Mensaje recibido: '" + message + "'");
            System.out.println("  - Content-Type: "
                    + rawMessage.getMessageProperties().getContentType());
            System.out.println("  - Timestamp: "
                    + rawMessage.getMessageProperties().getTimestamp());

            // Confirmación manual del mensaje (si usas manual acks)
            // channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            System.err.println("[✗] Error: " + e.getMessage());
            // Rechazar el mensaje y reencolarlo (si usas manual acks)
            // channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
            throw e;
        }
    }
}