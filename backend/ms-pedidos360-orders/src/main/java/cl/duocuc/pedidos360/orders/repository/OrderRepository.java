package cl.duocuc.pedidos360.orders.repository;

import cl.duocuc.pedidos360.orders.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerId(String customerId);
}
