package cl.duocuc.pedidos360.mqadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Datos para crear una cola.
 * withDlq=true crea además "<name>.dlq" enlazada al exchange dead-letter y configura la cola
 * principal para que sus mensajes rechazados o vencidos vayan a esa DLQ.
 */
public record QueueRequest(
        @NotBlank(message = "name es obligatorio")
        @Pattern(regexp = NamePatterns.NAME, message = "name " + NamePatterns.NAME_MESSAGE)
        String name,
        Boolean durable,
        Boolean withDlq,
        @Positive(message = "messageTtlMs debe ser mayor que 0")
        Long messageTtlMs,
        @Positive(message = "maxLength debe ser mayor que 0")
        Long maxLength) {

    public boolean durableOrDefault() {
        return durable == null || durable;
    }

    public boolean withDlqOrDefault() {
        return Boolean.TRUE.equals(withDlq);
    }
}
