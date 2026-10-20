package cl.duocuc.pedidos360.mqadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Datos para crear o quitar un binding entre un exchange y una cola. */
public record BindingRequest(
        @NotBlank(message = "exchange es obligatorio")
        @Pattern(regexp = NamePatterns.NAME, message = "exchange " + NamePatterns.NAME_MESSAGE)
        String exchange,
        @NotBlank(message = "queue es obligatorio")
        @Pattern(regexp = NamePatterns.NAME, message = "queue " + NamePatterns.NAME_MESSAGE)
        String queue,
        @NotNull(message = "routingKey es obligatorio (puede ir vacío en un exchange fanout)")
        @Pattern(regexp = NamePatterns.ROUTING_KEY, message = "routingKey " + NamePatterns.ROUTING_KEY_MESSAGE)
        String routingKey) {
}
