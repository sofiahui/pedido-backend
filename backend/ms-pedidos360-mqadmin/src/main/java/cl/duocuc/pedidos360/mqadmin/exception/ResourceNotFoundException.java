package cl.duocuc.pedidos360.mqadmin.exception;

/** La cola o el exchange indicado no existe en RabbitMQ (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
