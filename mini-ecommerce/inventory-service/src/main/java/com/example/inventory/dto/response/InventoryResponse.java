package com.example.inventory.dto.response;

import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        Integer quantity,
        boolean available
) {}
