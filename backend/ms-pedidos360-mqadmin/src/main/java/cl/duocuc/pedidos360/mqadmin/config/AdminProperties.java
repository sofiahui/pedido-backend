package cl.duocuc.pedidos360.mqadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Nombres del sistema leídos desde application.yml (prefijo app.admin). */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        String deadLetterExchange,
        List<String> protectedQueuePrefixes,
        List<String> protectedExchanges) {

    public AdminProperties {
        deadLetterExchange = deadLetterExchange == null ? "cmd.dead.dlx" : deadLetterExchange;
        protectedQueuePrefixes = protectedQueuePrefixes == null ? List.of() : List.copyOf(protectedQueuePrefixes);
        protectedExchanges = protectedExchanges == null ? List.of() : List.copyOf(protectedExchanges);
    }

    public boolean isProtectedQueue(String name) {
        return protectedQueuePrefixes.stream().anyMatch(name::startsWith);
    }

    public boolean isProtectedExchange(String name) {
        return protectedExchanges.contains(name);
    }
}
