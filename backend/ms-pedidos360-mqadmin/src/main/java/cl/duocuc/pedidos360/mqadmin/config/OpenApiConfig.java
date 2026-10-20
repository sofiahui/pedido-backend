package cl.duocuc.pedidos360.mqadmin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI pedidos360MqAdminOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pedidos360 - Administrador de RabbitMQ")
                        .description("Creación y eliminación de colas, exchanges y bindings de RabbitMQ (solo rol Admin)")
                        .version("v0.1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .schemaRequirement(BEARER_SCHEME, new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
