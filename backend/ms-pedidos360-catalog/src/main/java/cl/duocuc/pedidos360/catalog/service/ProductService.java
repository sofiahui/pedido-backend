package cl.duocuc.pedidos360.catalog.service;

import cl.duocuc.pedidos360.catalog.domain.Product;
import cl.duocuc.pedidos360.catalog.dto.ProductRequest;
import cl.duocuc.pedidos360.catalog.exception.InsufficientStockException;
import cl.duocuc.pedidos360.catalog.exception.ProductNotFoundException;
import cl.duocuc.pedidos360.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = getProduct(id);
        applyRequest(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }

    /**
     * delta negativo decrementa stock (al aceptar un pedido); nunca permite quedar bajo cero.
     */
    @Transactional
    public Product adjustStock(Long id, int delta) {
        Product product = getProduct(id);
        int newStock = product.getStock() + delta;
        if (newStock < 0) {
            throw new InsufficientStockException(id, product.getStock(), -delta);
        }
        product.setStock(newStock);
        return productRepository.save(product);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
    }
}
