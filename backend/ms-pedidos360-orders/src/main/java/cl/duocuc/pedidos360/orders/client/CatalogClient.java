package cl.duocuc.pedidos360.orders.client;

import cl.duocuc.pedidos360.orders.exception.StockUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Cliente HTTP hacia ms-pedidos360-catalog. Propaga el mismo Bearer token que llegó
 * al request original, ya que catalog también valida el JWT de forma independiente.
 */
@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(@Value("${downstream.catalog.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ProductInfo getProduct(Long productId) {
        try {
            return restClient.get()
                    .uri("/api/catalog/products/{id}", productId)
                    .header(HttpHeaders.AUTHORIZATION, currentAuthorizationHeader())
                    .retrieve()
                    .body(ProductInfo.class);
        } catch (RestClientResponseException ex) {
            throw new StockUnavailableException(
                    "No se pudo obtener el producto " + productId + " del catálogo: " + ex.getMessage());
        }
    }

    public void decrementStock(Long productId, int quantity) {
        try {
            restClient.patch()
                    .uri("/api/catalog/products/{id}/stock", productId)
                    .header(HttpHeaders.AUTHORIZATION, currentAuthorizationHeader())
                    .body(new StockAdjustRequest(-quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new StockUnavailableException(
                    "Stock insuficiente para el producto " + productId + ": " + ex.getResponseBodyAsString());
        }
    }

    private String currentAuthorizationHeader() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No hay contexto de request HTTP activo");
        }
        String header = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            throw new IllegalStateException("Falta el header Authorization en la petición entrante");
        }
        return header;
    }
}
