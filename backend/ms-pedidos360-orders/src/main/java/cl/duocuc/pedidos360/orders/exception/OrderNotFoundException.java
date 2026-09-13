package cl.duocuc.pedidos360.orders.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("No existe el pedido con id " + id);
    }
}
