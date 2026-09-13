package cl.duocuc.pedidos360.bff.exception;

import org.springframework.http.HttpStatusCode;

public class UpstreamServiceException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String responseBody;

    public UpstreamServiceException(HttpStatusCode statusCode, String responseBody) {
        super("El servicio interno respondió " + statusCode.value() + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
