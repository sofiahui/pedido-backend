package cl.duocuc.pedidos360.mqadmin.service;

import cl.duocuc.pedidos360.mqadmin.config.AdminProperties;
import cl.duocuc.pedidos360.mqadmin.dto.BindingRequest;
import cl.duocuc.pedidos360.mqadmin.dto.ExchangeKind;
import cl.duocuc.pedidos360.mqadmin.dto.ExchangeRequest;
import cl.duocuc.pedidos360.mqadmin.dto.QueueRequest;
import cl.duocuc.pedidos360.mqadmin.exception.ResourceConflictException;
import cl.duocuc.pedidos360.mqadmin.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba contra un RabbitMQ real (el del clúster en localhost:5672). Si no hay broker disponible
 * la prueba se omite, así que no rompe el build en equipos sin Docker.
 * Credenciales y host se pueden cambiar con RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER y RABBITMQ_PASS.
 */
class RabbitAdminServiceIntegrationTest {

    private CachingConnectionFactory connectionFactory;
    private RabbitAdmin rabbitAdmin;
    private RabbitAdminService service;
    private String suffix;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(
                env("RABBITMQ_HOST", "localhost"), Integer.parseInt(env("RABBITMQ_PORT", "5672")));
        connectionFactory.setUsername(env("RABBITMQ_USER", "pedidos360"));
        connectionFactory.setPassword(env("RABBITMQ_PASS", "pedidos360"));
        connectionFactory.setConnectionTimeout(1500);

        boolean available;
        try {
            connectionFactory.createConnection().close();
            available = true;
        } catch (Exception e) {
            available = false;
        }
        assumeTrue(available, "No hay un RabbitMQ disponible: se omite la prueba de integración");

        rabbitAdmin = new RabbitAdmin(connectionFactory);
        AdminProperties props = new AdminProperties(
                "cmd.dead.dlx", List.of("q.cmd."), List.of("cmd.direct", "cmd.topic", "cmd.dead.dlx"));
        service = new RabbitAdminService(rabbitAdmin, new RabbitTemplate(connectionFactory), props);
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void limpiar() {
        if (rabbitAdmin == null) {
            return;
        }
        rabbitAdmin.deleteQueue("it-" + suffix);
        rabbitAdmin.deleteQueue("it-" + suffix + ".dlq");
        rabbitAdmin.deleteExchange("it-ex-" + suffix);
        connectionFactory.destroy();
    }

    @Test
    void creaYEliminaColaExchangeYBindingEnUnRabbitReal() {
        String queue = "it-" + suffix;
        String exchange = "it-ex-" + suffix;

        // Cola con su DLQ
        var created = service.createQueue(new QueueRequest(queue, true, true, 60000L, 100L));
        assertThat(created.deadLetterQueue()).isEqualTo(queue + ".dlq");
        assertThat(service.getQueue(queue).messages()).isZero();
        assertThat(service.getQueue(queue + ".dlq").name()).isEqualTo(queue + ".dlq");

        // Crear la misma cola otra vez es un conflicto
        assertThatThrownBy(() -> service.createQueue(new QueueRequest(queue, true, false, null, null)))
                .isInstanceOf(ResourceConflictException.class);

        // Exchange topic + binding
        service.createExchange(new ExchangeRequest(exchange, ExchangeKind.TOPIC, true));
        assertThatThrownBy(() -> service.createExchange(new ExchangeRequest(exchange, ExchangeKind.TOPIC, true)))
                .isInstanceOf(ResourceConflictException.class);
        service.createBinding(new BindingRequest(exchange, queue, "ventas.#"));

        // Un binding hacia una cola que no existe es un 404
        assertThatThrownBy(() -> service.createBinding(new BindingRequest(exchange, "no-existe-" + suffix, "k")))
                .isInstanceOf(ResourceNotFoundException.class);

        // Limpieza por la propia API
        service.deleteBinding(new BindingRequest(exchange, queue, "ventas.#"));
        service.deleteExchange(exchange);
        service.deleteQueue(queue);
        service.deleteQueue(queue + ".dlq");

        assertThatThrownBy(() -> service.getQueue(queue)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteExchange(exchange)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lasColasDelSistemaSiguenProtegidas() {
        assertThatThrownBy(() -> service.deleteQueue("q.cmd.email")).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> service.deleteExchange("cmd.direct")).isInstanceOf(ResourceConflictException.class);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
