package com.example.inventory.controller;

import com.example.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management API")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/check")
    @Operation(summary = "Check product availability")
    public ResponseEntity<Boolean> check(
            @RequestParam UUID productId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(inventoryService.checkAvailability(productId, quantity));
    }
}
