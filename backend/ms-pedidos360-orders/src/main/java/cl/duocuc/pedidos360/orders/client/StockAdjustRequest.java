package cl.duocuc.pedidos360.orders.client;

/** delta negativo = decremento de stock (al aceptar un pedido). */
public record StockAdjustRequest(int delta) {
}
