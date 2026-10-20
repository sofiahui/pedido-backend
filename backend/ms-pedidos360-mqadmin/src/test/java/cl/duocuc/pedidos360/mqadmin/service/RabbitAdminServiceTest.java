package cl.duocuc.pedidos360.mqadmin.service;

import cl.duocuc.pedidos360.mqadmin.config.AdminProperties;
import cl.duocuc.pedidos360.mqadmin.dto.BindingRequest;
import cl.duocuc.pedidos360.mqadmin.dto.ExchangeKind;
import cl.duocuc.pedidos360.mqadmin.dto.ExchangeRequest;
import cl.duocuc.pedidos360.mqadmin.dto.QueueRequest;
import cl.duocuc.pedidos360.mqadmin.dto.QueueResponse;
import cl.duocuc.pedidos360.mqadmin.exception.ResourceConflictException;
import cl.duocuc.pedidos360.mqadmin.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    @Mock
    private AmqpAdmin amqpAdmin;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        AdminProperties props = new AdminProperties(
                "cmd.dead.dlx", List.of("q.cmd."), List.of("cmd.direct", "cmd.topic", "cmd.dead.dlx"));
        service = new RabbitAdminService(amqpAdmin, rabbitTemplate, props);
    }

    private void exchangeExists(boolean exists) {
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Object>>any())).thenReturn(exists);
    }

    // ---------------------------------------------------------------- colas

    @Test
    void creaUnaColaSimple() {
        when(amqpAdmin.getQueueInfo("cola.nueva")).thenReturn(null);

        QueueResponse response = service.createQueue(new QueueRequest("cola.nueva", null, null, null, null));

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("cola.nueva");
        assertThat(captor.getValue().isDurable()).isTrue();
        assertThat(captor.getValue().getArguments()).doesNotContainKey("x-dead-letter-exchange");
        assertThat(response.deadLetterQueue()).isNull();
    }

    @Test
    void creaUnaColaConSuDlqYLaEnlazaAlExchangeDeadLetter() {
        when(amqpAdmin.getQueueInfo(anyString())).thenReturn(null);

        QueueResponse response = service.createQueue(new QueueRequest("ventas", true, true, 60000L, 500L));

        ArgumentCaptor<Queue> queues = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin, times(2)).declareQueue(queues.capture());
        Queue dlq = queues.getAllValues().get(0);
        Queue main = queues.getAllValues().get(1);

        assertThat(dlq.getName()).isEqualTo("ventas.dlq");
        assertThat(main.getName()).isEqualTo("ventas");
        assertThat(main.getArguments())
                .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key", "ventas")
                .containsEntry("x-message-ttl", 60000L)
                .containsEntry("x-max-length", 500L);

        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(binding.capture());
        assertThat(binding.getValue().getExchange()).isEqualTo("cmd.dead.dlx");
        assertThat(binding.getValue().getDestination()).isEqualTo("ventas.dlq");
        assertThat(binding.getValue().getRoutingKey()).isEqualTo("ventas");
        assertThat(response.deadLetterQueue()).isEqualTo("ventas.dlq");
    }

    @Test
    void noCreaUnaColaQueYaExiste() {
        when(amqpAdmin.getQueueInfo("existente")).thenReturn(new QueueInformation("existente", 0, 0));

        assertThatThrownBy(() -> service.createQueue(new QueueRequest("existente", null, null, null, null)))
                .isInstanceOf(ResourceConflictException.class);

        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void siRabbitRechazaPorConfiguracionDistintaResponde409() {
        when(amqpAdmin.getQueueInfo(anyString())).thenReturn(null);
        when(amqpAdmin.declareQueue(any())).thenThrow(
                new AmqpIOException(new IOException("PRECONDITION_FAILED - inequivalent arg 'durable'")));

        assertThatThrownBy(() -> service.createQueue(new QueueRequest("cola", null, null, null, null)))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("configuración distinta");
    }

    @Test
    void noEliminaLasColasDelSistema() {
        assertThatThrownBy(() -> service.deleteQueue("q.cmd.email"))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("sistema");
        assertThatThrownBy(() -> service.deleteQueue("q.cmd.email.dlq"))
                .isInstanceOf(ResourceConflictException.class);

        verify(amqpAdmin, never()).deleteQueue(anyString());
    }

    @Test
    void eliminarUnaColaInexistenteDa404() {
        when(amqpAdmin.getQueueInfo("fantasma")).thenReturn(null);

        assertThatThrownBy(() -> service.deleteQueue("fantasma")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void eliminaUnaColaPropia() {
        when(amqpAdmin.getQueueInfo("ventas")).thenReturn(new QueueInformation("ventas", 0, 0));

        service.deleteQueue("ventas");

        verify(amqpAdmin).deleteQueue("ventas");
    }

    @Test
    void consultaUnaColaDevuelveMensajesYConsumidores() {
        when(amqpAdmin.getQueueInfo("ventas")).thenReturn(new QueueInformation("ventas", 4, 2));

        QueueResponse response = service.getQueue("ventas");

        assertThat(response.messages()).isEqualTo(4);
        assertThat(response.consumers()).isEqualTo(2);
    }

    // ----------------------------------------------------------- exchanges

    @Test
    void creaUnExchangeTopic() {
        exchangeExists(false);

        service.createExchange(new ExchangeRequest("eventos", ExchangeKind.TOPIC, null));

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TopicExchange.class);
        assertThat(captor.getValue().getName()).isEqualTo("eventos");
        assertThat(captor.getValue().isDurable()).isTrue();
    }

    @Test
    void noCreaExchangesConPrefijoReservadoNiRepetidos() {
        assertThatThrownBy(() -> service.createExchange(new ExchangeRequest("amq.mio", ExchangeKind.DIRECT, null)))
                .isInstanceOf(ResourceConflictException.class);

        exchangeExists(true);
        assertThatThrownBy(() -> service.createExchange(new ExchangeRequest("eventos", ExchangeKind.DIRECT, null)))
                .isInstanceOf(ResourceConflictException.class);

        verify(amqpAdmin, never()).declareExchange(any());
    }

    @Test
    void noEliminaLosExchangesDelSistema() {
        assertThatThrownBy(() -> service.deleteExchange("cmd.direct")).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> service.deleteExchange("cmd.topic")).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> service.deleteExchange("cmd.dead.dlx")).isInstanceOf(ResourceConflictException.class);

        verify(amqpAdmin, never()).deleteExchange(anyString());
    }

    @Test
    void eliminaUnExchangePropio() {
        exchangeExists(true);

        service.deleteExchange("eventos");

        verify(amqpAdmin).deleteExchange("eventos");
    }

    // ------------------------------------------------------------ bindings

    @Test
    void creaUnBindingEntreExchangeYCola() {
        exchangeExists(true);
        when(amqpAdmin.getQueueInfo("ventas")).thenReturn(new QueueInformation("ventas", 0, 0));

        service.createBinding(new BindingRequest("eventos", "ventas", "ventas.#"));

        ArgumentCaptor<Binding> captor = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(captor.capture());
        assertThat(captor.getValue().getExchange()).isEqualTo("eventos");
        assertThat(captor.getValue().getDestination()).isEqualTo("ventas");
        assertThat(captor.getValue().getRoutingKey()).isEqualTo("ventas.#");
    }

    @Test
    void unBindingConColaInexistenteDa404() {
        exchangeExists(true);
        when(amqpAdmin.getQueueInfo("fantasma")).thenReturn(null);

        assertThatThrownBy(() -> service.createBinding(new BindingRequest("eventos", "fantasma", "k")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void noQuitaLosBindingsDeLasColasDelSistema() {
        assertThatThrownBy(() -> service.deleteBinding(new BindingRequest("cmd.direct", "q.cmd.email", "email.send")))
                .isInstanceOf(ResourceConflictException.class);

        verify(amqpAdmin, never()).removeBinding(any());
    }

    @Test
    void quitaUnBindingPropio() {
        exchangeExists(true);
        when(amqpAdmin.getQueueInfo("ventas")).thenReturn(new QueueInformation("ventas", 0, 0));

        service.deleteBinding(new BindingRequest("eventos", "ventas", "ventas.#"));

        verify(amqpAdmin).removeBinding(any(Binding.class));
    }
}
