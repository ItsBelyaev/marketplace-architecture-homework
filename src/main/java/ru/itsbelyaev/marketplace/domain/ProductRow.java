package ru.itsbelyaev.marketplace.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductRow(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String category,
        String status,
        UUID sellerId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
