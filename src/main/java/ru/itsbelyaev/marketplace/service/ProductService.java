package ru.itsbelyaev.marketplace.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.domain.ProductRow;
import ru.itsbelyaev.marketplace.error.BusinessException;
import ru.itsbelyaev.marketplace.generated.model.ErrorCode;
import ru.itsbelyaev.marketplace.generated.model.ProductCreate;
import ru.itsbelyaev.marketplace.generated.model.ProductPage;
import ru.itsbelyaev.marketplace.generated.model.ProductResponse;
import ru.itsbelyaev.marketplace.generated.model.ProductStatus;
import ru.itsbelyaev.marketplace.generated.model.ProductUpdate;
import ru.itsbelyaev.marketplace.repository.ProductRepository;
import ru.itsbelyaev.marketplace.security.AuthenticatedUser;
import ru.itsbelyaev.marketplace.security.CurrentUser;

@Service
public class ProductService {
    private final ProductRepository products;
    private final CurrentUser currentUser;

    public ProductService(ProductRepository products, CurrentUser currentUser) {
        this.products = products;
        this.currentUser = currentUser;
    }

    public ProductResponse create(ProductCreate request) {
        AuthenticatedUser user = currentUser.get();
        requireSellerOrAdmin(user);
        return toResponse(products.insert(request, user.id()));
    }

    public ProductResponse get(UUID id) {
        return toResponse(requireProduct(id));
    }

    public ProductPage list(Integer page, Integer size, ProductStatus status, String category) {
        int currentPage = page == null ? 0 : page;
        int currentSize = size == null ? 20 : size;
        ProductRepository.PageResult result = products.list(currentPage, currentSize,
                status == null ? null : status.getValue(), category);
        List<ProductResponse> items = result.items().stream().map(this::toResponse).toList();
        return new ProductPage(items, result.total(), currentPage, currentSize);
    }

    public ProductResponse update(UUID id, ProductUpdate request) {
        ProductRow product = requireProduct(id);
        requireOwnerOrAdmin(product);
        return toResponse(products.update(id, request));
    }

    public void archive(UUID id) {
        ProductRow product = requireProduct(id);
        requireOwnerOrAdmin(product);
        products.archive(id);
    }

    private ProductRow requireProduct(UUID id) {
        return products.findById(id).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND, "Product not found",
                Map.of("product_id", id)));
    }

    private void requireSellerOrAdmin(AuthenticatedUser user) {
        if (!user.hasRole("SELLER") && !user.hasRole("ADMIN")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied");
        }
    }

    private void requireOwnerOrAdmin(ProductRow product) {
        AuthenticatedUser user = currentUser.get();
        requireSellerOrAdmin(user);
        if (!user.hasRole("ADMIN") && !product.sellerId().equals(user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                    "Seller can manage only own products");
        }
    }

    private ProductResponse toResponse(ProductRow row) {
        ProductResponse response = new ProductResponse(
                row.id(), row.name(), row.price(), row.stock(), row.category(),
                ProductStatus.fromValue(row.status()), row.sellerId(), row.createdAt(), row.updatedAt());
        response.setDescription(row.description());
        return response;
    }
}
