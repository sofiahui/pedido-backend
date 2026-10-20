package cl.duocuc.pedidos360.orders.repository;

import cl.duocuc.pedidos360.orders.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Pedidos de un cliente, con sus items cargados en la misma consulta. */
    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerId(String customerId);

    /** Todos los pedidos, con sus items. */
    @Override
    @EntityGraph(attributePaths = "items")
    List<Order> findAll();

    /** Un pedido por id, con sus items. */
    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(Long id);
}