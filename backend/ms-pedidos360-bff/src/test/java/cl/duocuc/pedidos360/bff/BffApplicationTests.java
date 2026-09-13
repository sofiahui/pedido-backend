package cl.duocuc.pedidos360.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifica que el contexto de Spring (incluida la configuración de seguridad
 * con JwtDecoder basado en JWKS lazy) arranca correctamente sin necesitar
 * conectividad real a Azure AD.
 */
@SpringBootTest
class BffApplicationTests {

    @Test
    void contextLoads() {
    }
}
