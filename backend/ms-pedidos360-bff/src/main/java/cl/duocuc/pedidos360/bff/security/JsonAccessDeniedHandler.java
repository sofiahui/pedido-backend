package cl.duocuc.pedidos360.bff.security;

import cl.duocuc.pedidos360.bff.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Responde 403 en formato JSON cuando el token es válido pero el rol
 * del usuario no tiene permiso sobre el endpoint solicitado.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        ApiError body = new ApiError(
                HttpStatus.FORBIDDEN.value(),
                "Acceso denegado",
                "El rol autenticado no tiene permisos para este recurso",
                Instant.now());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
