package cl.duocuc.pedidos360.notify.messaging.consumer;

import cl.duocuc.pedidos360.notify.exception.NonRecoverableCommandException;
import cl.duocuc.pedidos360.notify.messaging.CommandEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Verifica la política de ACK/NACK: lo que va a la DLQ, lo que se reintenta y lo que se ignora. */
class BaseCommandConsumerTest {

    private static final long TAG = 7L;

    private static final String VALID = """
            {"type":"EmailSend","eventId":"e-1","timestamp":"2026-10-20T10:00:00Z",
             "traceId":"t-1","correlationId":"1","payload":{"orderId":1,"status":"ACEPTADO"}}""";

    private Channel channel;
    private TestConsumer consumer;

    @BeforeEach
    void setUp() {
        channel = mock(Channel.class);
        consumer = new TestConsumer(new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void mensajeValidoSeProcesaYSeConfirmaConAck() throws IOException {
        consumer.handle(message(VALID, false), channel, "EmailSend");

        assertThat(consumer.processed).hasSize(1);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void jsonRotoVaALaDlqSinReintento() throws IOException {
        consumer.handle(message("esto no es json", false), channel, "EmailSend");

        assertThat(consumer.processed).isEmpty();
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void tipoEquivocadoVaALaDlq() throws IOException {
        consumer.handle(message(VALID, false), channel, "KitchenTicket");

        assertThat(consumer.processed).isEmpty();
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void mensajeSinOrderIdVaALaDlq() throws IOException {
        String sinOrderId = VALID.replace("\"orderId\":1,", "");

        consumer.handle(message(sinOrderId, false), channel, "EmailSend");

        assertThat(consumer.processed).isEmpty();
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void mensajeDuplicadoSeConfirmaSinReprocesar() throws IOException {
        consumer.handle(message(VALID, false), channel, "EmailSend");
        consumer.handle(message(VALID, false), channel, "EmailSend");

        assertThat(consumer.processed).hasSize(1);
        verify(channel, org.mockito.Mockito.times(2)).basicAck(TAG, false);
    }

    @Test
    void errorNoRecuperableVaALaDlqSinReintento() throws IOException {
        consumer.action = c -> {
            throw new NonRecoverableCommandException("datos inválidos");
        };

        consumer.handle(message(VALID, false), channel, "EmailSend");

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(TAG, false);
    }

    @Test
    void errorTemporalSeReintentaUnaVez() throws IOException {
        consumer.action = c -> {
            throw new IllegalStateException("servicio caído");
        };

        consumer.handle(message(VALID, false), channel, "EmailSend");

        verify(channel).basicNack(TAG, false, true);
    }

    @Test
    void errorTemporalQueFallaEnElReintentoVaALaDlq() throws IOException {
        consumer.action = c -> {
            throw new IllegalStateException("servicio caído");
        };

        consumer.handle(message(VALID, true), channel, "EmailSend");

        verify(channel).basicNack(TAG, false, false);
    }

    private static Message message(String body, boolean redelivered) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        props.setConsumerQueue("q.cmd.email");
        props.setRedelivered(redelivered);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }

    private static class TestConsumer extends BaseCommandConsumer {
        final List<CommandEnvelope> processed = new ArrayList<>();
        Consumer<CommandEnvelope> action = c -> { };

        TestConsumer(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        protected void process(CommandEnvelope command) {
            action.accept(command);
            processed.add(command);
        }
    }
}
