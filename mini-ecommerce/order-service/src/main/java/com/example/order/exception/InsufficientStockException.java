package com.example.order.exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int quantity) {
        super("Insufficient stock for productId: " + productId + ", requested: " + quantity);
    }
}
