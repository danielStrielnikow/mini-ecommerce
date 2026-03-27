package com.example.inventory.dto.response;

import java.util.UUID;

public record InventorySummaryResponse(
        UUID productId,
        Integer quantity,
        boolean available
) {}
