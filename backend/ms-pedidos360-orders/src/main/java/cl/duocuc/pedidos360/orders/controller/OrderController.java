package cl.duocuc.pedidos360.orders.controller;

import cl.duocuc.pedidos360.orders.domain.Order;
import cl.duocuc.pedidos360.orders.dto.ChangeStatusRequest;
import cl.duocuc.pedidos360.orders.dto.CreateOrderRequest;
import cl.duocuc.pedidos360.orders.dto.OrderItemResponse;
import cl.duocuc.pedidos360.orders.dto.OrderResponse;
import cl.duocuc.pedidos360.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        Order order = orderService.createOrder(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public List<OrderResponse> list(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        List<Order> orders = orderService.listOrders(jwt.getSubject(), isCustomerOnly(authentication));
        return orders.stream().map(OrderController::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public OrderResponse getOne(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        Order order = orderService.getOrder(id);
        if (isCustomerOnly(authentication) && !order.getCustomerId().equals(jwt.getSubject())) {
            throw new AccessDeniedException("No puede consultar pedidos de otro cliente");
        }
        return toResponse(order);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        Order order = orderService.changeStatus(id, request.status());
        return toResponse(order);
    }

    private boolean isCustomerOnly(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .allMatch(authority -> authority.equals("ROLE_CLIENTE"));
    }

    private static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.subtotal()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getDeliveredAt(),
                items);
    }
}
