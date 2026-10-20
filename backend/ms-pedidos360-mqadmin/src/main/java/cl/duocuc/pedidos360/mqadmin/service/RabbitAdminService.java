package cl.duocuc.pedidos360.mqadmin.service;

import cl.duocuc.pedidos360.mqadmin.config.AdminProperties;
import cl.duocuc.pedidos360.mqadmin.dto.BindingRequest;
import cl.duocuc.pedidos360.mqadmin.dto.BindingResponse;
import cl.duocuc.pedidos360.mqadmin.dto.ExchangeRequest;
import cl.duocuc.pedidos360.mqadmin.dto.ExchangeResponse;
import cl.duocuc.pedidos360.mqadmin.dto.QueueRequest;
import cl.duocuc.pedidos360.mqadmin.dto.QueueResponse;
import cl.duocuc.pedidos360.mqadmin.exception.ResourceConflictException;
import cl.duocuc.pedidos360.mqadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Toda la lógica de administración de RabbitMQ. El controlador solo llama a estos métodos y
 * no conoce RabbitMQ. Usa AmqpAdmin (declarar/borrar) y un canal pasivo para saber si existe algo.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);
    private static final String DLQ_SUFFIX = ".dlq";

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final AdminProperties props;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, AdminProperties props) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    // ---------------------------------------------------------------- colas

    public QueueResponse createQueue(QueueRequest request) {
        String name = request.name();
        boolean withDlq = request.withDlqOrDefault();
        String dlqName = name + DLQ_SUFFIX;

        if (queueExists(name)) {
            throw new ResourceConflictException("La cola '" + name + "' ya existe");
        }
        if (withDlq && queueExists(dlqName)) {
            throw new ResourceConflictException("La DLQ '" + dlqName + "' ya existe");
        }

        Map<String, Object> args = new HashMap<>();
        if (request.messageTtlMs() != null) {
            args.put("x-message-ttl", request.messageTtlMs());
        }
        if (request.maxLength() != null) {
            args.put("x-max-length", request.maxLength());
        }

        declare("la cola '" + name + "'", () -> {
            if (withDlq) {
                // La DLQ y su enlace se crean primero para que la cola principal nazca ya conectada a ella.
                DirectExchange deadLetterExchange = new DirectExchange(props.deadLetterExchange(), true, false);
                amqpAdmin.declareExchange(deadLetterExchange);
                Queue dlq = new Queue(dlqName, true, false, false);
                amqpAdmin.declareQueue(dlq);
                amqpAdmin.declareBinding(BindingBuilder.bind(dlq).to(deadLetterExchange).with(name));
                args.put("x-dead-letter-exchange", props.deadLetterExchange());
                args.put("x-dead-letter-routing-key", name);
            }
            amqpAdmin.declareQueue(new Queue(name, request.durableOrDefault(), false, false, args));
            return null;
        });

        log.info("Cola creada: {} (durable={}, dlq={})", name, request.durableOrDefault(), withDlq);
        return new QueueResponse(name, request.durableOrDefault(), withDlq ? dlqName : null, null, null);
    }

    public QueueResponse getQueue(String name) {
        QueueInformation info = amqpAdmin.getQueueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        return new QueueResponse(info.getName(), null, null, info.getMessageCount(), info.getConsumerCount());
    }

    public void deleteQueue(String name) {
        if (props.isProtectedQueue(name)) {
            throw new ResourceConflictException("La cola '" + name + "' es del sistema y no se puede eliminar");
        }
        if (!queueExists(name)) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        amqpAdmin.deleteQueue(name);
        log.info("Cola eliminada: {}", name);
    }

    // ----------------------------------------------------------- exchanges

    public ExchangeResponse createExchange(ExchangeRequest request) {
        String name = request.name();
        if (name.startsWith("amq.")) {
            throw new ResourceConflictException("El prefijo 'amq.' está reservado por RabbitMQ");
        }
        if (exchangeExists(name)) {
            throw new ResourceConflictException("El exchange '" + name + "' ya existe");
        }

        boolean durable = request.durableOrDefault();
        AbstractExchange exchange = switch (request.type()) {
            case DIRECT -> new DirectExchange(name, durable, false);
            case TOPIC -> new TopicExchange(name, durable, false);
            case FANOUT -> new FanoutExchange(name, durable, false);
        };
        declare("el exchange '" + name + "'", () -> {
            amqpAdmin.declareExchange(exchange);
            return null;
        });

        log.info("Exchange creado: {} (tipo={}, durable={})", name, request.type(), durable);
        return new ExchangeResponse(name, request.type(), durable);
    }

    public void deleteExchange(String name) {
        if (props.isProtectedExchange(name) || name.startsWith("amq.")) {
            throw new ResourceConflictException("El exchange '" + name + "' es del sistema y no se puede eliminar");
        }
        if (!exchangeExists(name)) {
            throw new ResourceNotFoundException("El exchange '" + name + "' no existe");
        }
        amqpAdmin.deleteExchange(name);
        log.info("Exchange eliminado: {}", name);
    }

    // ------------------------------------------------------------ bindings

    public BindingResponse createBinding(BindingRequest request) {
        requireExistingEndpoints(request);
        declare("el binding " + describe(request), () -> {
            amqpAdmin.declareBinding(toBinding(request));
            return null;
        });
        log.info("Binding creado: {}", describe(request));
        return new BindingResponse(request.exchange(), request.queue(), request.routingKey());
    }

    public void deleteBinding(BindingRequest request) {
        if (props.isProtectedQueue(request.queue())) {
            throw new ResourceConflictException(
                    "Los bindings de la cola '" + request.queue() + "' son del sistema y no se pueden eliminar");
        }
        requireExistingEndpoints(request);
        amqpAdmin.removeBinding(toBinding(request));
        log.info("Binding eliminado: {}", describe(request));
    }

    // ----------------------------------------------------------- auxiliares

    private void requireExistingEndpoints(BindingRequest request) {
        if (!exchangeExists(request.exchange())) {
            throw new ResourceNotFoundException("El exchange '" + request.exchange() + "' no existe");
        }
        if (!queueExists(request.queue())) {
            throw new ResourceNotFoundException("La cola '" + request.queue() + "' no existe");
        }
    }

    private Binding toBinding(BindingRequest request) {
        return new Binding(request.queue(), Binding.DestinationType.QUEUE,
                request.exchange(), request.routingKey(), null);
    }

    private String describe(BindingRequest request) {
        return request.exchange() + " -> " + request.queue() + " [" + request.routingKey() + "]";
    }

    private boolean queueExists(String name) {
        return amqpAdmin.getQueueInfo(name) != null;
    }

    /** Declaración pasiva: si el exchange no existe RabbitMQ cierra el canal y se interpreta como "no existe". */
    private boolean exchangeExists(String name) {
        Boolean exists = rabbitTemplate.execute(channel -> {
            try {
                channel.exchangeDeclarePassive(name);
                return true;
            } catch (IOException e) {
                return false;
            }
        });
        return Boolean.TRUE.equals(exists);
    }

    /** Convierte el rechazo "PRECONDITION_FAILED" de RabbitMQ (mismo nombre, otra configuración) en un 409. */
    private <T> T declare(String what, Supplier<T> action) {
        try {
            return action.get();
        } catch (AmqpIOException e) {
            if (hasPreconditionFailed(e)) {
                throw new ResourceConflictException(
                        "No se pudo crear " + what + ": ya existe con una configuración distinta");
            }
            throw e;
        }
    }

    private static boolean hasPreconditionFailed(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("PRECONDITION_FAILED")) {
                return true;
            }
        }
        return false;
    }
}
