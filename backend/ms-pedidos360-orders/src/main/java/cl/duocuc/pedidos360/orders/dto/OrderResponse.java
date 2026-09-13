package cl.duocuc.pedidos360.orders.dto;

import cl.duocuc.pedidos360.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String customerId,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt,
        List<OrderItemResponse> items
) {
}
