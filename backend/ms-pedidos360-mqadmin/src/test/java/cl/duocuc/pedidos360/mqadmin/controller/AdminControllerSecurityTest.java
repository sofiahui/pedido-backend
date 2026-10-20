package cl.duocuc.pedidos360.mqadmin.controller;

import cl.duocuc.pedidos360.mqadmin.dto.QueueResponse;
import cl.duocuc.pedidos360.mqadmin.exception.ResourceConflictException;
import cl.duocuc.pedidos360.mqadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.ConnectException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica rol Admin obligatorio, validación de entrada (400) y mapeo de errores (404/409). */
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService adminService;

    private static RequestPostProcessor conRol(String role) {
        return jwt().authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static final String COLA_VALIDA = "{\"name\":\"ventas\",\"withDlq\":true}";

    @Test
    void sinTokenResponde401EnJson() throws Exception {
        mockMvc.perform(post("/api/admin/queues").contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(adminService);
    }

    @Test
    void conRolDistintoDeAdminResponde403EnJson() throws Exception {
        for (String rol : List.of("CLIENTE", "OPERADOR", "AUDITOR")) {
            mockMvc.perform(post("/api/admin/queues").with(conRol(rol))
                            .contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        verifyNoInteractions(adminService);
    }

    @Test
    void adminCreaUnaColaYRecibe201() throws Exception {
        when(adminService.createQueue(any())).thenReturn(new QueueResponse("ventas", true, "ventas.dlq", null, null));

        mockMvc.perform(post("/api/admin/queues").with(conRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("ventas"))
                .andExpect(jsonPath("$.deadLetterQueue").value("ventas.dlq"));
    }

    @Test
    void nombreDeColaInvalidoResponde400() throws Exception {
        mockMvc.perform(post("/api/admin/queues").with(conRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"nombre con espacios!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("letras")));

        verifyNoInteractions(adminService);
    }

    @Test
    void nombreVacioYTtlNegativoResponden400() throws Exception {
        mockMvc.perform(post("/api/admin/queues").with(conRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"messageTtlMs\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tipoDeExchangeFueraDeLaListaResponde400() throws Exception {
        mockMvc.perform(post("/api/admin/exchanges").with(conRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"eventos\",\"type\":\"headers\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("direct, topic o fanout")));

        mockMvc.perform(post("/api/admin/exchanges").with(conRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"eventos\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elTipoDeExchangeAceptaMayusculasYMinusculas() throws Exception {
        mockMvc.perform(post("/api/admin/exchanges").with(conRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"eventos\",\"type\":\"TOPIC\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void bindingSinRoutingKeyResponde400() throws Exception {
        mockMvc.perform(post("/api/admin/bindings").with(conRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exchange\":\"eventos\",\"queue\":\"ventas\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/admin/bindings").with(conRol("ADMIN"))
                        .param("exchange", "eventos").param("queue", "ventas"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nombreInvalidoEnLaRutaResponde400() throws Exception {
        mockMvc.perform(delete("/api/admin/queues/mala$cola").with(conRol("ADMIN")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminService);
    }

    @Test
    void unRecursoProtegidoResponde409() throws Exception {
        doThrow(new ResourceConflictException("La cola 'q.cmd.email' es del sistema y no se puede eliminar"))
                .when(adminService).deleteQueue("q.cmd.email");

        mockMvc.perform(delete("/api/admin/queues/q.cmd.email").with(conRol("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sistema")));
    }

    @Test
    void eliminarUnaColaPropiaResponde204() throws Exception {
        mockMvc.perform(delete("/api/admin/queues/ventas").with(conRol("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void sinConexionConRabbitResponde503() throws Exception {
        when(adminService.createQueue(any())).thenThrow(new AmqpConnectException(new ConnectException("Connection refused")));

        mockMvc.perform(post("/api/admin/queues").with(conRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
