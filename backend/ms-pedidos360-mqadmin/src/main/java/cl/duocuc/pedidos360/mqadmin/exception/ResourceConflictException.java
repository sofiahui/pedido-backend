package cl.duocuc.pedidos360.mqadmin.exception;

/** El recurso ya existe, tiene otra configuración o es del sistema y está protegido (HTTP 409). */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
