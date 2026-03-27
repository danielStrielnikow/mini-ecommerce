package com.example.inventory.service;

public record InventoryFilter(
        Boolean available,
        Integer minQuantity,
        Integer maxQuantity
) {}
