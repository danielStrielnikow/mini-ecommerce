package com.example.product.dto.response;

import com.example.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String name,
        BigDecimal price,
        ProductStatus status
) {}
