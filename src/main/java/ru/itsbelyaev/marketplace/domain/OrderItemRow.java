package ru.itsbelyaev.marketplace.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRow(UUID id, UUID orderId, UUID productId, int quantity, BigDecimal priceAtOrder) {
}
