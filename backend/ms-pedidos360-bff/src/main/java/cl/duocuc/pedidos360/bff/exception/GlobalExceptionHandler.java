package cl.duocuc.pedidos360.bff.exception;

import cl.duocuc.pedidos360.bff.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<String> handleUpstream(UpstreamServiceException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        ApiError body = new ApiError(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Error interno",
                ex.getMessage(),
                Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
