package ru.itsbelyaev.marketplace.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderRow(
        UUID id,
        UUID userId,
        String status,
        UUID promoCodeId,
        String promoCode,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
