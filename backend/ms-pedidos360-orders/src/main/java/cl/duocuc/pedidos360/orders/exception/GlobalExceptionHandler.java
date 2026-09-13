package cl.duocuc.pedidos360.orders.exception;

import cl.duocuc.pedidos360.orders.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(OrderNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Pedido no encontrado", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(InvalidOrderTransitionException ex) {
        return build(HttpStatus.CONFLICT, "Transición de estado inválida", ex.getMessage());
    }

    @ExceptionHandler(StockUnavailableException.class)
    public ResponseEntity<ApiError> handleStockUnavailable(StockUnavailableException ex) {
        return build(HttpStatus.CONFLICT, "Stock no disponible", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Datos inválidos");
        return build(HttpStatus.BAD_REQUEST, "Validación fallida", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ex.getMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message) {
        ApiError body = new ApiError(status.value(), error, message, Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
