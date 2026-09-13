package cl.duocuc.pedidos360.catalog.controller;

import cl.duocuc.pedidos360.catalog.domain.Product;
import cl.duocuc.pedidos360.catalog.dto.ProductRequest;
import cl.duocuc.pedidos360.catalog.dto.ProductResponse;
import cl.duocuc.pedidos360.catalog.dto.StockAdjustRequest;
import cl.duocuc.pedidos360.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public List<ProductResponse> list() {
        return productService.listProducts().stream().map(ProductController::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ProductResponse getOne(@PathVariable Long id) {
        return toResponse(productService.getProduct(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return toResponse(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustRequest request) {
        return toResponse(productService.adjustStock(id, request.delta()));
    }

    private static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
