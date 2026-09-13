package cl.duocuc.pedidos360.orders.exception;

import cl.duocuc.pedidos360.orders.domain.OrderStatus;

public class InvalidOrderTransitionException extends RuntimeException {

    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("No se puede pasar el pedido de " + from + " a " + to);
    }
}
