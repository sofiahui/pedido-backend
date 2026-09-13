package cl.duocuc.pedidos360.orders.exception;

public class StockUnavailableException extends RuntimeException {

    public StockUnavailableException(String message) {
        super(message);
    }
}
