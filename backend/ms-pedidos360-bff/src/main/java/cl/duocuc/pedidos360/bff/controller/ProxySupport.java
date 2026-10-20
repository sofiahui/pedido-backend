package cl.duocuc.pedidos360.bff.controller;

import cl.duocuc.pedidos360.bff.exception.UpstreamServiceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Lógica común de los controladores proxy del BFF. */
final class ProxySupport {

    private ProxySupport() {
    }

    /**
     * Envía la petición al microservicio propagando el Bearer token original. Devuelve solo el código,
     * el tipo (JSON) y el cuerpo: no copia cabeceras de transporte del microservicio (Content-Length,
     * Transfer-Encoding...), que romperían la respuesta que el BFF arma por su cuenta.
     */
    static ResponseEntity<String> forward(RestClient.RequestHeadersSpec<?> request, String authorization) {
        try {
            ResponseEntity<String> upstream = request
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .toEntity(String.class);
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(upstream.getBody());
        } catch (RestClientResponseException ex) {
            throw new UpstreamServiceException(ex.getStatusCode(), ex.getResponseBodyAsString());
        }
    }

    /** Los cuerpos llegan como texto; hay que declararlos JSON o el microservicio responde 415. */
    static RestClient.RequestBodySpec json(RestClient.RequestBodySpec spec) {
        return spec.contentType(MediaType.APPLICATION_JSON);
    }
}
