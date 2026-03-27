package com.example.order.dto.response;

import com.example.order.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        UUID productId,
        OrderStatus status,
        BigDecimal totalPrice
) {}
