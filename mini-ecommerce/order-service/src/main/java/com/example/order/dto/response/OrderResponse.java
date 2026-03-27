package com.example.order.dto.response;

import com.example.order.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID productId,
        Integer quantity,
        OrderStatus status,
        BigDecimal totalPrice,
        Instant createdAt
) {}
