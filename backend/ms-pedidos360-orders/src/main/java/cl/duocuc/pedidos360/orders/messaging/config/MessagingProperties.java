package cl.duocuc.pedidos360.orders.messaging.config;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges, colas y claves de enrutamiento.
 * Se leen desde application.yml (prefijo app.rabbit).
 */
@ConfigurationProperties(prefix = "app.rabbit")
public record MessagingProperties(
        Exchanges exchanges,
        Flow email,
        Flow kitchen,
        Flow invoice) {

    public record Exchanges(String direct, String topic, String deadLetter) {}

    public record Flow(String queue, String dlq, String routingKey, String topicPattern) {}
}