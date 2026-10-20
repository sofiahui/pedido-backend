package cl.duocuc.pedidos360.notify.messaging.consumer.dlq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeadLetterConsumerTest {

    private static Message deadLetter(String body, Map<String, Object> xDeath) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(3L);
        props.setConsumerQueue("q.cmd.email.dlq");
        props.setMessageId("evt-9");
        if (xDeath != null) {
            props.setHeader("x-death", List.of(xDeath));
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void elDetalleIncluyeColaDeOrigenMotivoYPayload() {
        Message message = deadLetter("{\"orderId\":5}",
                Map.of("queue", "q.cmd.email", "reason", "rejected", "count", 1L));

        String detalle = DeadLetterConsumer.describe(message);

        assertThat(detalle)
                .contains("dlq=q.cmd.email.dlq")
                .contains("colaOrigen=q.cmd.email")
                .contains("motivo=rejected")
                .contains("messageId=evt-9")
                .contains("{\"orderId\":5}");
    }

    @Test
    void sinCabeceraXDeathIndicaQueEsDesconocido() {
        String detalle = DeadLetterConsumer.describe(deadLetter("x", null));

        assertThat(detalle).contains("colaOrigen=desconocido").contains("motivo=desconocido");
    }

    @Test
    void unPayloadMuyLargoSeTrunca() {
        String detalle = DeadLetterConsumer.describe(deadLetter("a".repeat(5000), null));

        assertThat(detalle).contains("(truncado)");
        assertThat(detalle.length()).isLessThan(1500);
    }

    @Test
    void despuesDeRegistrarConfirmaConAck() throws IOException {
        Channel channel = mock(Channel.class);

        new DeadLetterConsumer().onDeadLetter(deadLetter("x", null), channel);

        verify(channel).basicAck(3L, false);
    }
}
