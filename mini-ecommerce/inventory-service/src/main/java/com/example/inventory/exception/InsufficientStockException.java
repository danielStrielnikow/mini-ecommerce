package com.example.inventory.exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int available, int requested) {
        super("Insufficient stock for productId: " + productId
                + " — available: " + available + ", requested: " + requested);
    }
}
