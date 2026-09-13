package cl.duocuc.pedidos360.orders.service;

import cl.duocuc.pedidos360.orders.client.CatalogClient;
import cl.duocuc.pedidos360.orders.client.ProductInfo;
import cl.duocuc.pedidos360.orders.domain.Order;
import cl.duocuc.pedidos360.orders.domain.OrderItem;
import cl.duocuc.pedidos360.orders.domain.OrderStatus;
import cl.duocuc.pedidos360.orders.dto.CreateOrderRequest;
import cl.duocuc.pedidos360.orders.dto.OrderItemRequest;
import cl.duocuc.pedidos360.orders.exception.InvalidOrderTransitionException;
import cl.duocuc.pedidos360.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la regla clave del caso: "no se puede despachar sin aceptar" y que
 * el stock se descuenta exactamente al aceptar el pedido.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CatalogClient catalogClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, catalogClient);
    }

    @Test
    void creaPedidoConsultandoPrecioYNombreDelCatalogo() {
        when(catalogClient.getProduct(10L)).thenReturn(new ProductInfo(10L, "Pizza", BigDecimal.valueOf(9990), 20, true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(10L, 2)));
        Order order = orderService.createOrder("customer-1", request);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREADO);
        assertThat(order.getTotal()).isEqualByComparingTo("19980");
        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    void noPermiteDespacharSinAceptar() {
        Order order = orderConEstado(OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.DESPACHADO))
                .isInstanceOf(InvalidOrderTransitionException.class);

        verify(catalogClient, never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    void aceptarPedidoDecrementaStockDeCadaItem() {
        Order order = orderConEstado(OrderStatus.CREADO);
        OrderItem item = new OrderItem();
        item.setProductId(10L);
        item.setQuantity(3);
        item.setUnitPrice(BigDecimal.TEN);
        order.addItem(item);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order updated = orderService.changeStatus(1L, OrderStatus.ACEPTADO);

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.ACEPTADO);
        verify(catalogClient).decrementStock(10L, 3);
    }

    @Test
    void flujoCompletoHastaEntregadoEsValido() {
        Order order = orderConEstado(OrderStatus.DESPACHADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order updated = orderService.changeStatus(1L, OrderStatus.ENTREGADO);

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.ENTREGADO);
        assertThat(updated.getDeliveredAt()).isNotNull();
    }

    private Order orderConEstado(OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId("customer-1");
        order.setStatus(status);
        return order;
    }
}
