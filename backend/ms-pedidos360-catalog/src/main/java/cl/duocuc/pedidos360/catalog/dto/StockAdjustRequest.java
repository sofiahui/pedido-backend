package cl.duocuc.pedidos360.catalog.dto;

import jakarta.validation.constraints.NotNull;

/** delta negativo decrementa stock (al aceptar un pedido), positivo lo repone. */
public record StockAdjustRequest(@NotNull Integer delta) {
}
