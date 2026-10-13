package ru.itsbelyaev.marketplace.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PromoCodeRow(
        UUID id,
        String code,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        int maxUses,
        int currentUses,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        boolean active
) {
}
