package cl.duocuc.pedidos360.orders.service;

import cl.duocuc.pedidos360.orders.client.CatalogClient;
import cl.duocuc.pedidos360.orders.client.ProductInfo;
import cl.duocuc.pedidos360.orders.domain.Order;
import cl.duocuc.pedidos360.orders.domain.OrderItem;
import cl.duocuc.pedidos360.orders.domain.OrderStatus;
import cl.duocuc.pedidos360.orders.dto.CreateOrderRequest;
import cl.duocuc.pedidos360.orders.dto.OrderItemRequest;
import cl.duocuc.pedidos360.orders.exception.InvalidOrderTransitionException;
import cl.duocuc.pedidos360.orders.exception.OrderNotFoundException;
import cl.duocuc.pedidos360.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regla de negocio central: la máquina de estados de un pedido.
 * CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO, con CANCELADO
 * disponible hasta antes de DESPACHADO. El mapa de transiciones impide, por
 * construcción, saltarse "aceptar" antes de "despachar".
 */
@Service
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.CREADO, EnumSet.of(OrderStatus.ACEPTADO, OrderStatus.CANCELADO));
        ALLOWED_TRANSITIONS.put(OrderStatus.ACEPTADO, EnumSet.of(OrderStatus.EN_PREPARACION, OrderStatus.CANCELADO));
        ALLOWED_TRANSITIONS.put(OrderStatus.EN_PREPARACION, EnumSet.of(OrderStatus.DESPACHADO, OrderStatus.CANCELADO));
        ALLOWED_TRANSITIONS.put(OrderStatus.DESPACHADO, EnumSet.of(OrderStatus.ENTREGADO));
        ALLOWED_TRANSITIONS.put(OrderStatus.ENTREGADO, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELADO, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;

    public OrderService(OrderRepository orderRepository, CatalogClient catalogClient) {
        this.orderRepository = orderRepository;
        this.catalogClient = catalogClient;
    }

    @Transactional
    public Order createOrder(String customerId, CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREADO);

        for (OrderItemRequest itemRequest : request.items()) {
            ProductInfo product = catalogClient.getProduct(itemRequest.productId());

            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setProductName(product.name());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(product.price());
            order.addItem(item);
        }

        order.recalculateTotal();
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> listOrders(String customerId, boolean restrictToOwn) {
        return restrictToOwn ? orderRepository.findByCustomerId(customerId) : orderRepository.findAll();
    }

    @Transactional
    public Order changeStatus(Long id, OrderStatus newStatus) {
        Order order = getOrder(id);
        OrderStatus current = order.getStatus();

        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new InvalidOrderTransitionException(current, newStatus);
        }

        if (newStatus == OrderStatus.ACEPTADO) {
            for (OrderItem item : order.getItems()) {
                catalogClient.decrementStock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.ENTREGADO) {
            order.setDeliveredAt(Instant.now());
        }

        return orderRepository.save(order);
    }
}
