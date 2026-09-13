package cl.duocuc.pedidos360.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
