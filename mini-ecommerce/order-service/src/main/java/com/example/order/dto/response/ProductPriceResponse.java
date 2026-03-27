package com.example.order.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductPriceResponse(UUID id, String name, BigDecimal price, String status) {}
