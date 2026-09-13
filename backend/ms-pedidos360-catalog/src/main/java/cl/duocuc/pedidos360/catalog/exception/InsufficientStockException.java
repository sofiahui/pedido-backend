package cl.duocuc.pedidos360.catalog.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int available, int requested) {
        super("Stock insuficiente para el producto " + productId
                + " (disponible: " + available + ", solicitado: " + requested + ")");
    }
}
