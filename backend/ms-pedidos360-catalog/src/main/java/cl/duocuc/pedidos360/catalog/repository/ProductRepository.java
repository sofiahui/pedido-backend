package cl.duocuc.pedidos360.catalog.repository;

import cl.duocuc.pedidos360.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
