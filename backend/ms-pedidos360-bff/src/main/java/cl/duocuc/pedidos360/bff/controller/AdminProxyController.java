package cl.duocuc.pedidos360.bff.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * Enruta el administrador de RabbitMQ (ms-pedidos360-mqadmin). Solo el rol Admin puede usarlo;
 * el microservicio vuelve a validar el token y el rol por su cuenta.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProxyController {

    private final RestClient mqadminRestClient;

    public AdminProxyController(@Qualifier("mqadminRestClient") RestClient mqadminRestClient) {
        this.mqadminRestClient = mqadminRestClient;
    }

    @PostMapping("/queues")
    public ResponseEntity<String> createQueue(@RequestBody String body,
                                               @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ProxySupport.forward(ProxySupport.json(mqadminRestClient.post().uri("/api/admin/queues")).body(body), authorization);
    }

    @GetMapping("/queues/{name}")
    public ResponseEntity<String> getQueue(@PathVariable String name,
                                            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ProxySupport.forward(mqadminRestClient.get().uri("/api/admin/queues/{name}", name), authorization);
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<String> deleteQueue(@PathVariable String name,
                                               @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ProxySupport.forward(mqadminRestClient.delete().uri("/api/admin/queues/{name}", name), authorization);
    }

    @PostMapping("/exchanges")
    public ResponseEntity<String> createExchange(@RequestBody String body,
                                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ProxySupport.forward(ProxySupport.json(mqadminRestClient.post().uri("/api/admin/exchanges")).body(body), authorization);
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<String> deleteExchange(@PathVariable String name,
                                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ProxySupport.forward(mqadminRestClient.delete().uri("/api/admin/exchanges/{name}", name), authorization);
    }

    @PostMapping("/bindings")
    public ResponseEntity<String> createBinding(@RequestBody String body,
                                                 @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ProxySupport.forward(ProxySupport.json(mqadminRestClient.post().uri("/api/admin/bindings")).body(body), authorization);
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<String> deleteBinding(@RequestParam String exchange,
                                                 @RequestParam String queue,
                                                 @RequestParam String routingKey,
                                                 @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ProxySupport.forward(
                mqadminRestClient.delete().uri(uri -> uri.path("/api/admin/bindings")
                        .queryParam("exchange", "{exchange}")
                        .queryParam("queue", "{queue}")
                        .queryParam("routingKey", "{routingKey}")
                        .build(exchange, queue, routingKey)),
                authorization);
    }
}
