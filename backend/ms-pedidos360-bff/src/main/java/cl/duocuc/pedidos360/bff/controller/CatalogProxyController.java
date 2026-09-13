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
 * Enruta las peticiones de catálogo hacia ms-pedidos360-catalog, propagando el
 * Bearer token original para que el microservicio downstream también lo valide.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogProxyController {

    private final RestClient catalogRestClient;

    public CatalogProxyController(@Qualifier("catalogRestClient") RestClient catalogRestClient) {
        this.catalogRestClient = catalogRestClient;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<String> listProducts(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(catalogRestClient.get().uri("/api/catalog/products"), authorization);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<String> getProduct(@PathVariable String id,
                                              @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(catalogRestClient.get().uri("/api/catalog/products/{id}", id), authorization);
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> createProduct(@RequestBody String body,
                                                 @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(catalogRestClient.post().uri("/api/catalog/products").body(body), authorization);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> updateProduct(@PathVariable String id,
                                                 @RequestBody String body,
                                                 @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(catalogRestClient.put().uri("/api/catalog/products/{id}", id).body(body), authorization);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteProduct(@PathVariable String id,
                                                 @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return forward(catalogRestClient.delete().uri("/api/catalog/products/{id}", id), authorization);
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
