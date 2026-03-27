package com.example.order.service;

import com.example.order.entity.enums.OrderStatus;

import java.util.UUID;

public record OrderFilter(OrderStatus status, UUID productId) {}
