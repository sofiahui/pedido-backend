package cl.duocuc.pedidos360.notify.exception;

/** Error que no se arregla reintentando (datos inválidos): el mensaje va directo a la DLQ. */
public class NonRecoverableCommandException extends RuntimeException {
    public NonRecoverableCommandException(String message) {
        super(message);
    }
}