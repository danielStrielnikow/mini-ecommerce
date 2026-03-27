package com.example.product.dto.response;

import com.example.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        ProductStatus status,
        Instant createdAt
) {}
