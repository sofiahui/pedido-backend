package cl.duocuc.pedidos360.orders.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    /** Un rol sin permiso debe terminar en 403, no en 500 por el handler genérico. */
    @Test
    void unAccesoDenegadoResponde403() {
        var response = new GlobalExceptionHandler().handleAccessDenied(new AccessDeniedException("sin permiso"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().status()).isEqualTo(403);
    }
}
