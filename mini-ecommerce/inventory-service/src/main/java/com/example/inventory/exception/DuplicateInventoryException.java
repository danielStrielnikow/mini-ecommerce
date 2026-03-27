package com.example.inventory.exception;

import java.util.UUID;

public class DuplicateInventoryException extends RuntimeException {

    public DuplicateInventoryException(UUID productId) {
        super("Inventory already exists for productId: " + productId);
    }
}
