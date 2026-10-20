package cl.duocuc.pedidos360.mqadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Datos para crear un exchange. */
public record ExchangeRequest(
        @NotBlank(message = "name es obligatorio")
        @Pattern(regexp = NamePatterns.NAME, message = "name " + NamePatterns.NAME_MESSAGE)
        String name,
        @NotNull(message = "type es obligatorio (direct, topic o fanout)")
        ExchangeKind type,
        Boolean durable) {

    public boolean durableOrDefault() {
        return durable == null || durable;
    }
}
