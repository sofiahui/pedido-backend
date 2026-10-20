package cl.duocuc.pedidos360.bff.controller;

import cl.duocuc.pedidos360.bff.exception.UpstreamServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Levanta un servidor HTTP falso que hace de microservicio interno y comprueba lo que el BFF
 * le envía (Content-Type y Authorization) y lo que devuelve al frontend.
 */
class ProxyForwardingTest {

    private HttpServer downstream;
    private RestClient restClient;
    private final List<String> receivedContentTypes = new ArrayList<>();
    private final List<String> receivedAuthorizations = new ArrayList<>();
    private final List<String> receivedBodies = new ArrayList<>();
    private final List<String> receivedUris = new ArrayList<>();

    @BeforeEach
    void startDownstream() throws IOException {
        downstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        downstream.createContext("/", exchange -> {
            receivedContentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedUris.add(exchange.getRequestURI().toString());

            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/404")) {
                byte[] error = "{\"status\":404,\"error\":\"No encontrado\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, error.length);
                exchange.getResponseBody().write(error);
            } else if (exchange.getRequestMethod().equals("DELETE")) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                // Longitud 0 = respuesta "chunked", como las que da Tomcat con cuerpos grandes.
                byte[] json = "{\"id\":1,\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, 0);
                exchange.getResponseBody().write(json);
            }
            exchange.close();
        });
        downstream.start();
        restClient = RestClient.builder().baseUrl("http://localhost:" + downstream.getAddress().getPort()).build();
    }

    @AfterEach
    void stopDownstream() {
        downstream.stop(0);
    }

    @Test
    void elBodyJsonSeReenviaComoApplicationJson() {
        OrdersProxyController controller = new OrdersProxyController(restClient);

        controller.createOrder("{\"items\":[]}", "Bearer abc");
        controller.changeStatus("1", "{\"status\":\"ACEPTADO\"}", "Bearer abc");

        assertThat(receivedContentTypes).hasSize(2).allSatisfy(type -> assertThat(type).startsWith("application/json"));
        assertThat(receivedBodies).containsExactly("{\"items\":[]}", "{\"status\":\"ACEPTADO\"}");
    }

    @Test
    void elTokenSeReenviaAlMicroservicio() {
        CatalogProxyController controller = new CatalogProxyController(restClient);

        controller.createProduct("{\"name\":\"Pizza\"}", "Bearer token-original");
        controller.updateProduct("3", "{\"name\":\"Pizza\"}", "Bearer token-original");

        assertThat(receivedAuthorizations).containsOnly("Bearer token-original");
        assertThat(receivedContentTypes).allSatisfy(type -> assertThat(type).startsWith("application/json"));
    }

    @Test
    void laRespuestaNoArrastraCabecerasDeTransporteDelMicroservicio() {
        OrdersProxyController controller = new OrdersProxyController(restClient);

        ResponseEntity<String> response = controller.createOrder("{}", "Bearer abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo("{\"id\":1,\"ok\":true}");
        assertThat(response.getHeaders().containsKey(HttpHeaders.TRANSFER_ENCODING)).isFalse();
        assertThat(response.getHeaders().containsKey(HttpHeaders.CONTENT_LENGTH)).isFalse();
    }

    @Test
    void unErrorDelMicroservicioSeRelayaConSuCodigoYCuerpo() {
        OrdersProxyController controller = new OrdersProxyController(restClient);

        assertThatThrownBy(() -> controller.getOrder("404", "Bearer abc"))
                .isInstanceOfSatisfying(UpstreamServiceException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getResponseBody()).contains("No encontrado");
                });
    }

    @Test
    void unDeleteConRespuestaVaciaDevuelve204() {
        CatalogProxyController controller = new CatalogProxyController(restClient);

        ResponseEntity<String> response = controller.deleteProduct("7", "Bearer abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void elAdministradorRecibeJsonYLosParametrosDelBinding() {
        AdminProxyController controller = new AdminProxyController(restClient);

        controller.createQueue("{\"name\":\"ventas\"}", "Bearer abc");
        controller.deleteBinding("eventos", "ventas", "ventas.#", "Bearer abc");

        assertThat(receivedContentTypes.get(0)).startsWith("application/json");
        assertThat(receivedUris.get(1))
                .startsWith("/api/admin/bindings?")
                .contains("exchange=eventos")
                .contains("queue=ventas")
                .contains("routingKey=ventas.%23");
    }
}
