package cl.duocuc.pedidos360.catalog.service;

import cl.duocuc.pedidos360.catalog.domain.Product;
import cl.duocuc.pedidos360.catalog.exception.InsufficientStockException;
import cl.duocuc.pedidos360.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void decrementaStockCuandoHaySuficiente() {
        Product product = productConStock(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.adjustStock(1L, -3);

        assertThat(updated.getStock()).isEqualTo(7);
    }

    @Test
    void rechazaDecrementoQueDejaStockNegativo() {
        Product product = productConStock(2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.adjustStock(1L, -5))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void permiteReponerStock() {
        Product product = productConStock(5);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.adjustStock(1L, 10);

        assertThat(updated.getStock()).isEqualTo(15);
    }

    private Product productConStock(int stock) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Producto de prueba");
        product.setPrice(BigDecimal.TEN);
        product.setStock(stock);
        return product;
    }
}
