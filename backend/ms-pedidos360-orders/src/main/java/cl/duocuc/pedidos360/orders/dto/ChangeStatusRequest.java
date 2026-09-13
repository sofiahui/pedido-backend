package cl.duocuc.pedidos360.orders.dto;

import cl.duocuc.pedidos360.orders.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull OrderStatus status) {
}
