package cl.duocuc.pedidos360.bff.controller;

import cl.duocuc.pedidos360.bff.exception.UpstreamServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Enruta las peticiones de pedidos hacia ms-pedidos360-orders, propagando el
 * Bearer token original para que el microservicio downstream también lo valide.
 */
@RestController
@RequestMapping("/api/orders")
public class OrdersProxyController {

    private final RestClient ordersRestClient;

    public OrdersProxyController(@Qualifier("ordersRestClient") RestClient ordersRestClient) {
        this.ordersRestClient = ordersRestClient;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','CLIENTE')")
    public ResponseEntity<String> listOrders(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(ordersRestClient.get().uri("/api/orders"), authorization);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','CLIENTE')")
    public ResponseEntity<String> getOrder(@PathVariable String id,
                                            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(ordersRestClient.get().uri("/api/orders/{id}", id), authorization);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','CLIENTE')")
    public ResponseEntity<String> createOrder(@RequestBody String body,
                                               @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(ordersRestClient.post().uri("/api/orders").body(body), authorization);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<String> changeStatus(@PathVariable String id,
                                                @RequestBody String body,
                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(ordersRestClient.patch().uri("/api/orders/{id}/status", id).body(body), authorization);
    }

    private ResponseEntity<String> forward(RestClient.RequestHeadersSpec<?> request, String authorization) {
        try {
            return request
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException ex) {
            throw new UpstreamServiceException(ex.getStatusCode(), ex.getResponseBodyAsString());
        }
    }
}
