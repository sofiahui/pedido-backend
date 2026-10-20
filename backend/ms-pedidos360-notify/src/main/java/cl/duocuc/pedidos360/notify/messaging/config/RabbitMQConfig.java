package cl.duocuc.pedidos360.notify.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lado consumidor de la topología. Declara las colas que notify escucha y sus DLQ,
 * con los MISMOS argumentos que orders (si difieren, RabbitMQ responde PRECONDITION_FAILED).
 * Así notify puede arrancar aunque orders aún no lo haya hecho.
 * Los exchanges cmd.direct y cmd.topic y sus bindings los declara orders (el productor).
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitMQConfig {

    private final MessagingProperties props;

    public RabbitMQConfig(MessagingProperties props) {
        this.props = props;
    }

    @Bean
    public DirectExchange cmdDeadLetterExchange() {
        return new DirectExchange(props.exchanges().deadLetter(), true, false);
    }

    // ---- Email ----
    @Bean public Queue emailQueue() { return mainQueue(props.email()); }
    @Bean public Queue emailDlq()   { return QueueBuilder.durable(props.email().dlq()).build(); }
    @Bean public Binding emailDlqBinding() {
        return BindingBuilder.bind(emailDlq()).to(cmdDeadLetterExchange()).with(props.email().routingKey());
    }

    // ---- Kitchen ----
    @Bean public Queue kitchenQueue() { return mainQueue(props.kitchen()); }
    @Bean public Queue kitchenDlq()   { return QueueBuilder.durable(props.kitchen().dlq()).build(); }
    @Bean public Binding kitchenDlqBinding() {
        return BindingBuilder.bind(kitchenDlq()).to(cmdDeadLetterExchange()).with(props.kitchen().routingKey());
    }

    // ---- Invoice ----
    @Bean public Queue invoiceQueue() { return mainQueue(props.invoice()); }
    @Bean public Queue invoiceDlq()   { return QueueBuilder.durable(props.invoice().dlq()).build(); }
    @Bean public Binding invoiceDlqBinding() {
        return BindingBuilder.bind(invoiceDlq()).to(cmdDeadLetterExchange()).with(props.invoice().routingKey());
    }

    /** Igual que en orders: el NACK sin reencolar envía el mensaje a su DLQ. */
    private Queue mainQueue(MessagingProperties.Flow flow) {
        return QueueBuilder.durable(flow.queue())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(flow.routingKey())
                .build();
    }
}