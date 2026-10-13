package ru.itsbelyaev.marketplace.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itsbelyaev.marketplace.config.AppProperties;
import ru.itsbelyaev.marketplace.domain.OrderItemRow;
import ru.itsbelyaev.marketplace.domain.OrderRow;
import ru.itsbelyaev.marketplace.domain.ProductRow;
import ru.itsbelyaev.marketplace.domain.PromoCodeRow;
import ru.itsbelyaev.marketplace.error.BusinessException;
import ru.itsbelyaev.marketplace.generated.model.ErrorCode;
import ru.itsbelyaev.marketplace.generated.model.OrderCreate;
import ru.itsbelyaev.marketplace.generated.model.OrderItemRequest;
import ru.itsbelyaev.marketplace.generated.model.OrderItemResponse;
import ru.itsbelyaev.marketplace.generated.model.OrderResponse;
import ru.itsbelyaev.marketplace.generated.model.OrderStatus;
import ru.itsbelyaev.marketplace.generated.model.OrderUpdate;
import ru.itsbelyaev.marketplace.repository.OrderRepository;
import ru.itsbelyaev.marketplace.repository.ProductRepository;
import ru.itsbelyaev.marketplace.repository.PromoCodeRepository;
import ru.itsbelyaev.marketplace.repository.UserOperationRepository;
import ru.itsbelyaev.marketplace.security.AuthenticatedUser;
import ru.itsbelyaev.marketplace.security.CurrentUser;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final ProductRepository products;
    private final PromoCodeRepository promoCodes;
    private final UserOperationRepository operations;
    private final CurrentUser currentUser;
    private final AppProperties properties;

    public OrderService(OrderRepository orders, ProductRepository products, PromoCodeRepository promoCodes,
                        UserOperationRepository operations, CurrentUser currentUser, AppProperties properties) {
        this.orders = orders;
        this.products = products;
        this.promoCodes = promoCodes;
        this.operations = operations;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    @Transactional
    public OrderResponse create(OrderCreate request) {
        AuthenticatedUser user = requireOrderRole();
        checkRateLimit(user.id(), "CREATE_ORDER");
        if (orders.hasActive(user.id())) {
            throw new BusinessException(HttpStatus.CONFLICT, ErrorCode.ORDER_HAS_ACTIVE,
                    "User already has an active order");
        }

        Map<UUID, Integer> quantities = normalize(request.getItems());
        Map<UUID, ProductRow> productMap = lockAndValidateProducts(quantities);
        BigDecimal subtotal = subtotal(quantities, productMap);
        AppliedPromo appliedPromo = applyNewPromo(request.getPromoCode(), subtotal);

        quantities.forEach((id, quantity) -> products.adjustStock(id, -quantity));
        OrderRow order = orders.insert(user.id(), appliedPromo.id(), appliedPromo.total(), appliedPromo.discount());
        orders.insertItems(toItemRows(order.id(), quantities, productMap));
        if (appliedPromo.id() != null) {
            promoCodes.changeUsage(appliedPromo.id(), 1);
        }
        operations.insert(user.id(), "CREATE_ORDER");
        return loadResponse(order.id());
    }

    public OrderResponse get(UUID id) {
        AuthenticatedUser user = requireOrderRole();
        OrderRow order = requireOrder(id);
        requireOwner(order, user);
        return toResponse(order, orders.findItems(id));
    }

    @Transactional
    public OrderResponse update(UUID id, OrderUpdate request) {
        AuthenticatedUser user = requireOrderRole();
        OrderRow order = requireOrderForUpdate(id);
        requireOwner(order, user);
        if (!"CREATED".equals(order.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE_TRANSITION,
                    "Order can be updated only in CREATED status");
        }
        checkRateLimit(user.id(), "UPDATE_ORDER");

        List<OrderItemRow> oldItems = orders.findItems(id);
        products.findAllForUpdate(oldItems.stream().map(OrderItemRow::productId).toList());
        oldItems.forEach(item -> products.adjustStock(item.productId(), item.quantity()));

        Map<UUID, Integer> quantities = normalize(request.getItems());
        Map<UUID, ProductRow> productMap = lockAndValidateProducts(quantities);
        BigDecimal subtotal = subtotal(quantities, productMap);
        AppliedPromo appliedPromo = recalculateExistingPromo(order, subtotal);

        quantities.forEach((productId, quantity) -> products.adjustStock(productId, -quantity));
        orders.deleteItems(id);
        orders.insertItems(toItemRows(id, quantities, productMap));
        orders.update(id, appliedPromo.id(), appliedPromo.total(), appliedPromo.discount());
        operations.insert(user.id(), "UPDATE_ORDER");
        return loadResponse(id);
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        AuthenticatedUser user = requireOrderRole();
        OrderRow order = requireOrderForUpdate(id);
        requireOwner(order, user);
        if (!List.of("CREATED", "PAYMENT_PENDING").contains(order.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE_TRANSITION,
                    "Order cannot be canceled from status " + order.status());
        }

        List<OrderItemRow> items = orders.findItems(id);
        products.findAllForUpdate(items.stream().map(OrderItemRow::productId).toList());
        items.forEach(item -> products.adjustStock(item.productId(), item.quantity()));
        if (order.promoCodeId() != null) {
            promoCodes.findByIdForUpdate(order.promoCodeId());
            promoCodes.changeUsage(order.promoCodeId(), -1);
        }
        orders.cancel(id);
        return loadResponse(id);
    }

    private AuthenticatedUser requireOrderRole() {
        AuthenticatedUser user = currentUser.get();
        if (!user.hasRole("USER") && !user.hasRole("ADMIN")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied");
        }
        return user;
    }

    private void requireOwner(OrderRow order, AuthenticatedUser user) {
        if (!user.hasRole("ADMIN") && !order.userId().equals(user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, ErrorCode.ORDER_OWNERSHIP_VIOLATION,
                    "Order belongs to another user");
        }
    }

    private void checkRateLimit(UUID userId, String operationType) {
        if (properties.orderLimitMinutes() <= 0) {
            return;
        }
        OffsetDateTime border = OffsetDateTime.now().minusMinutes(properties.orderLimitMinutes());
        operations.last(userId, operationType).filter(time -> time.isAfter(border)).ifPresent(time -> {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.ORDER_LIMIT_EXCEEDED,
                    "Order operation rate limit exceeded",
                    Map.of("limit_minutes", properties.orderLimitMinutes(), "last_operation", time.toString()));
        });
    }

    private Map<UUID, Integer> normalize(List<OrderItemRequest> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            if (quantities.putIfAbsent(item.getProductId(), item.getQuantity()) != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "Duplicate product in order", Map.of("product_id", item.getProductId()));
            }
        }
        return quantities;
    }

    private Map<UUID, ProductRow> lockAndValidateProducts(Map<UUID, Integer> quantities) {
        Map<UUID, ProductRow> productMap = products.findAllForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductRow::id, Function.identity()));

        for (UUID productId : quantities.keySet()) {
            ProductRow product = productMap.get(productId);
            if (product == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND,
                        "Product not found", Map.of("product_id", productId));
            }
            if (!"ACTIVE".equals(product.status())) {
                throw new BusinessException(HttpStatus.CONFLICT, ErrorCode.PRODUCT_INACTIVE,
                        "Product is not active", Map.of("product_id", productId));
            }
        }

        List<Map<String, Object>> insufficient = new ArrayList<>();
        quantities.forEach((productId, quantity) -> {
            ProductRow product = productMap.get(productId);
            if (product.stock() < quantity) {
                insufficient.add(Map.of(
                        "product_id", productId,
                        "requested", quantity,
                        "available", product.stock()));
            }
        });
        if (!insufficient.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK,
                    "Insufficient stock", Map.of("products", insufficient));
        }
        return productMap;
    }

    private BigDecimal subtotal(Map<UUID, Integer> quantities, Map<UUID, ProductRow> productMap) {
        return quantities.entrySet().stream()
                .map(entry -> productMap.get(entry.getKey()).price()
                        .multiply(BigDecimal.valueOf(entry.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private AppliedPromo applyNewPromo(String code, BigDecimal subtotal) {
        if (code == null) {
            return new AppliedPromo(null, BigDecimal.ZERO.setScale(2), subtotal);
        }
        PromoCodeRow promo = promoCodes.findByCodeForUpdate(code)
                .orElseThrow(this::invalidPromo);
        validatePromo(promo, false);
        if (subtotal.compareTo(promo.minOrderAmount()) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.PROMO_CODE_MIN_AMOUNT,
                    "Order amount is below promo code minimum",
                    Map.of("minimum", promo.minOrderAmount(), "actual", subtotal));
        }
        BigDecimal discount = discount(subtotal, promo);
        return new AppliedPromo(promo.id(), discount, subtotal.subtract(discount).setScale(2));
    }

    private AppliedPromo recalculateExistingPromo(OrderRow order, BigDecimal subtotal) {
        if (order.promoCodeId() == null) {
            return new AppliedPromo(null, BigDecimal.ZERO.setScale(2), subtotal);
        }
        PromoCodeRow promo = promoCodes.findByIdForUpdate(order.promoCodeId())
                .orElseThrow(this::invalidPromo);
        validatePromo(promo, true);
        if (subtotal.compareTo(promo.minOrderAmount()) < 0) {
            promoCodes.changeUsage(promo.id(), -1);
            return new AppliedPromo(null, BigDecimal.ZERO.setScale(2), subtotal);
        }
        BigDecimal discount = discount(subtotal, promo);
        return new AppliedPromo(promo.id(), discount, subtotal.subtract(discount).setScale(2));
    }

    private void validatePromo(PromoCodeRow promo, boolean alreadyUsedByOrder) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean usesInvalid = alreadyUsedByOrder
                ? promo.currentUses() > promo.maxUses()
                : promo.currentUses() >= promo.maxUses();
        if (!promo.active() || usesInvalid || now.isBefore(promo.validFrom()) || now.isAfter(promo.validUntil())) {
            throw invalidPromo();
        }
    }

    private BigDecimal discount(BigDecimal subtotal, PromoCodeRow promo) {
        BigDecimal value;
        if ("PERCENTAGE".equals(promo.discountType())) {
            value = subtotal.multiply(promo.discountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal cap = subtotal.multiply(new BigDecimal("0.70")).setScale(2, RoundingMode.HALF_UP);
            value = value.min(cap);
        } else {
            value = promo.discountValue().min(subtotal);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BusinessException invalidPromo() {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.PROMO_CODE_INVALID,
                "Promo code is invalid");
    }

    private List<OrderItemRow> toItemRows(UUID orderId, Map<UUID, Integer> quantities,
                                          Map<UUID, ProductRow> productMap) {
        return quantities.entrySet().stream()
                .map(entry -> new OrderItemRow(UUID.randomUUID(), orderId, entry.getKey(), entry.getValue(),
                        productMap.get(entry.getKey()).price()))
                .toList();
    }

    private OrderRow requireOrder(UUID id) {
        return orders.findById(id).orElseThrow(() -> orderNotFound(id));
    }

    private OrderRow requireOrderForUpdate(UUID id) {
        return orders.findForUpdate(id).orElseThrow(() -> orderNotFound(id));
    }

    private BusinessException orderNotFound(UUID id) {
        return new BusinessException(HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND,
                "Order not found", Map.of("order_id", id));
    }

    private OrderResponse loadResponse(UUID id) {
        OrderRow order = requireOrder(id);
        return toResponse(order, orders.findItems(id));
    }

    private OrderResponse toResponse(OrderRow row, List<OrderItemRow> items) {
        List<OrderItemResponse> responseItems = items.stream()
                .map(item -> new OrderItemResponse(item.id(), item.productId(), item.quantity(), item.priceAtOrder()))
                .toList();
        OrderResponse response = new OrderResponse(
                row.id(), row.userId(), OrderStatus.fromValue(row.status()), row.totalAmount(),
                row.discountAmount(), responseItems, row.createdAt(), row.updatedAt());
        response.setPromoCode(row.promoCode());
        return response;
    }

    private record AppliedPromo(UUID id, BigDecimal discount, BigDecimal total) {
    }
}
