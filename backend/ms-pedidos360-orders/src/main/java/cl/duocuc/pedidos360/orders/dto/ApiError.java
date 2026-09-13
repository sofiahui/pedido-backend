package cl.duocuc.pedidos360.orders.dto;

import java.time.Instant;

public record ApiError(int status, String error, String message, Instant timestamp) {
}
