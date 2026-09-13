package cl.duocuc.pedidos360.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "El pedido debe tener al menos un item") @Valid List<OrderItemRequest> items
) {
}
