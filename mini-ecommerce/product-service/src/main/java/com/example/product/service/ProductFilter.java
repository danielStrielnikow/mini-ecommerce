package com.example.product.service;

import com.example.product.entity.ProductStatus;

import java.math.BigDecimal;

public record ProductFilter(
        String name,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status
) {}
