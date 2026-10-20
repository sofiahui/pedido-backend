package cl.duocuc.pedidos360.mqadmin.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Tipos de exchange permitidos. En el JSON se escriben en minúsculas: direct, topic, fanout. */
public enum ExchangeKind {
    DIRECT, TOPIC, FANOUT;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ExchangeKind fromJson(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "type '" + value + "' no es válido; use direct, topic o fanout"));
    }
}
