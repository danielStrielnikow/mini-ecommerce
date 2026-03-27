package com.example.order.exception;

import com.example.order.entity.enums.OrderStatus;

import java.util.UUID;

public class OrderStatusTransitionException extends RuntimeException {

    public OrderStatusTransitionException(UUID orderId, OrderStatus current, OrderStatus target) {
        super("Cannot transition order " + orderId + " from " + current + " to " + target);
    }
}
