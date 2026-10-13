package ru.itsbelyaev.marketplace.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.generated.api.ProductsApiDelegate;
import ru.itsbelyaev.marketplace.generated.model.ProductCreate;
import ru.itsbelyaev.marketplace.generated.model.ProductPage;
import ru.itsbelyaev.marketplace.generated.model.ProductResponse;
import ru.itsbelyaev.marketplace.generated.model.ProductStatus;
import ru.itsbelyaev.marketplace.generated.model.ProductUpdate;
import ru.itsbelyaev.marketplace.service.ProductService;

@Service
public class ProductsApiDelegateImpl implements ProductsApiDelegate {
    private final ProductService productService;

    public ProductsApiDelegateImpl(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public ResponseEntity<ProductResponse> createProduct(ProductCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @Override
    public ResponseEntity<ProductResponse> getProduct(UUID id) {
        return ResponseEntity.ok(productService.get(id));
    }

    @Override
    public ResponseEntity<ProductPage> listProducts(Integer page, Integer size, ProductStatus status,
                                                     String category) {
        return ResponseEntity.ok(productService.list(page, size, status, category));
    }

    @Override
    public ResponseEntity<ProductResponse> updateProduct(UUID id, ProductUpdate request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @Override
    public ResponseEntity<Void> archiveProduct(UUID id) {
        productService.archive(id);
        return ResponseEntity.noContent().build();
    }
}
