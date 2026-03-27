package com.example.inventory.service;

import java.util.UUID;

public interface InventoryService {

    boolean checkAvailability(UUID productId, int quantity);

    void decreaseStock(UUID productId, int quantity);
}
