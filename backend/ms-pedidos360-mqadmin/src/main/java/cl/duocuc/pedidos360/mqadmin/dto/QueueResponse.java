package cl.duocuc.pedidos360.mqadmin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Resultado de crear o consultar una cola (los campos que no aplican se omiten). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueResponse(
        String name,
        Boolean durable,
        String deadLetterQueue,
        Integer messages,
        Integer consumers) {
}
