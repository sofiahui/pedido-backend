package cl.duocuc.pedidos360.mqadmin.exception;

import cl.duocuc.pedidos360.mqadmin.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ResourceConflictException ex) {
        return build(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    /** Errores de @Valid en el cuerpo de la petición o en los parámetros de consulta. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Validación fallida", message);
    }

    /** Errores de @Pattern en variables de la ruta. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Validación fallida", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return build(HttpStatus.BAD_REQUEST, "Cuerpo de la petición inválido",
                cause == null ? "JSON mal formado" : cause.getMessage());
    }

    /** Sin este handler, Exception.class convertiría un 403 de @PreAuthorize en un 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Acceso denegado",
                "El rol autenticado no tiene permisos para este recurso");
    }

    @ExceptionHandler(AmqpConnectException.class)
    public ResponseEntity<ApiError> handleNoBroker(AmqpConnectException ex) {
        log.error("Sin conexión con RabbitMQ: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ no disponible",
                "No hay conexión con RabbitMQ; reintente en unos segundos");
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ApiError> handleAmqp(AmqpException ex) {
        log.error("RabbitMQ rechazó la operación: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "Error de RabbitMQ", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Error inesperado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ex.getMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), error, message, Instant.now()));
    }
}
