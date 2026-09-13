package cl.duocuc.pedidos360.orders.client;

import java.math.BigDecimal;

/**
 * Representación mínima del producto tal como lo expone ms-pedidos360-catalog.
 */
public record ProductInfo(Long id, String name, BigDecimal price, Integer stock, boolean active) {
}
