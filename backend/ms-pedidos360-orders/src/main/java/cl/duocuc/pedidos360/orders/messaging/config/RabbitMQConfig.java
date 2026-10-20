package cl.duocuc.pedidos360.orders.messaging.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * Topología RabbitMQ de Pedidos360: 3 flujos de trabajo (email, kitchen, invoice),
 * cada uno con su cola principal y su DLQ.
 *
 * Exchanges: cmd.direct (clave exacta), cmd.topic (patrones), cmd.dead.dlx (DLQ).
 * Solo declara la topología: no contiene lógica de negocio.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    private final MessagingProperties props;

    public RabbitMQConfig(MessagingProperties props) {
        this.props = props;
    }

    // ---------------- Exchanges ----------------

    @Bean
    public DirectExchange cmdDirectExchange() {
        return new DirectExchange(props.exchanges().direct(), true, false);
    }

    @Bean
    public TopicExchange cmdTopicExchange() {
        return new TopicExchange(props.exchanges().topic(), true, false);
    }

    @Bean
    public DirectExchange cmdDeadLetterExchange() {
        return new DirectExchange(props.exchanges().deadLetter(), true, false);
    }

    // ---------------- Flujo EMAIL ----------------

    @Bean
    public Queue emailQueue() {
        return mainQueue(props.email());
    }

    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(props.email().dlq()).build();
    }

    @Bean
    public Binding emailDirectBinding() {
        return BindingBuilder.bind(emailQueue()).to(cmdDirectExchange())
                .with(props.email().routingKey());
    }

    @Bean
    public Binding emailTopicBinding() {
        return BindingBuilder.bind(emailQueue()).to(cmdTopicExchange())
                .with(props.email().topicPattern());
    }

    @Bean
    public Binding emailDlqBinding() {
        return BindingBuilder.bind(emailDlq()).to(cmdDeadLetterExchange())
                .with(props.email().routingKey());
    }

    // ---------------- Flujo KITCHEN ----------------

    @Bean
    public Queue kitchenQueue() {
        return mainQueue(props.kitchen());
    }

    @Bean
    public Queue kitchenDlq() {
        return QueueBuilder.durable(props.kitchen().dlq()).build();
    }

    @Bean
    public Binding kitchenDirectBinding() {
        return BindingBuilder.bind(kitchenQueue()).to(cmdDirectExchange())
                .with(props.kitchen().routingKey());
    }

    @Bean
    public Binding kitchenTopicBinding() {
        return BindingBuilder.bind(kitchenQueue()).to(cmdTopicExchange())
                .with(props.kitchen().topicPattern());
    }

    @Bean
    public Binding kitchenDlqBinding() {
        return BindingBuilder.bind(kitchenDlq()).to(cmdDeadLetterExchange())
                .with(props.kitchen().routingKey());
    }

    // ---------------- Flujo INVOICE ----------------

    @Bean
    public Queue invoiceQueue() {
        return mainQueue(props.invoice());
    }

    @Bean
    public Queue invoiceDlq() {
        return QueueBuilder.durable(props.invoice().dlq()).build();
    }

    @Bean
    public Binding invoiceDirectBinding() {
        return BindingBuilder.bind(invoiceQueue()).to(cmdDirectExchange())
                .with(props.invoice().routingKey());
    }

    @Bean
    public Binding invoiceTopicBinding() {
        return BindingBuilder.bind(invoiceQueue()).to(cmdTopicExchange())
                .with(props.invoice().topicPattern());
    }

    @Bean
    public Binding invoiceDlqBinding() {
        return BindingBuilder.bind(invoiceDlq()).to(cmdDeadLetterExchange())
                .with(props.invoice().routingKey());
    }

    /** Serializa los mensajes como JSON (el sobre CommandEnvelope). */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // ---------------- Arranque ----------------

    /**
     * Declara exchanges, colas y bindings al iniciar el servicio.
     * Si RabbitMQ no está disponible, solo registra una advertencia y el servicio
     * arranca igual: la topología se declarará cuando se abra la primera conexión.
     */
    @Bean
    public ApplicationRunner declararTopologiaAlIniciar(AmqpAdmin amqpAdmin) {
        return args -> {
            try {
                amqpAdmin.initialize();
                log.info("Topología RabbitMQ declarada correctamente");
            } catch (Exception e) {
                log.warn("RabbitMQ no disponible al iniciar; la topología se declarará al conectar: {}",
                        e.getMessage());
            }
        };
    }

    // ---------------- Auxiliar ----------------

    /**
     * Cola principal durable. Los mensajes rechazados (nack sin reencolar)
     * o expirados van al exchange dead-letter con una clave fija,
     * para que siempre encuentren su DLQ.
     */
    private Queue mainQueue(MessagingProperties.Flow flow) {
        return QueueBuilder.durable(flow.queue())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(flow.routingKey())
                .build();
    }
}