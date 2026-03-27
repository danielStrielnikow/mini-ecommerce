package com.example.inventory.controller;

import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.dto.request.RestockRequest;
import com.example.inventory.dto.response.InventoryResponse;
import com.example.inventory.service.InventoryFilter;
import com.example.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management API")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "List inventory records with optional filters")
    public ResponseEntity<Page<InventoryResponse>> findAll(
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Integer minQuantity,
            @RequestParam(required = false) Integer maxQuantity,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        InventoryFilter filter = new InventoryFilter(available, minQuantity, maxQuantity);
        return ResponseEntity.ok(inventoryService.findAll(filter, pageable));
    }

    @GetMapping("/check")
    @Operation(summary = "Check product availability")
    public ResponseEntity<Boolean> check(
            @RequestParam UUID productId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(inventoryService.checkAvailability(productId, quantity));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory by product ID")
    public ResponseEntity<InventoryResponse> getByProductId(@PathVariable UUID productId) {
        return ResponseEntity.ok(inventoryService.getByProductId(productId));
    }

    @PostMapping
    @Operation(summary = "Create inventory record for a product")
    public ResponseEntity<InventoryResponse> create(@Valid @RequestBody CreateInventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.create(request));
    }

    @PatchMapping("/{productId}/restock")
    @Operation(summary = "Restock product inventory")
    public ResponseEntity<InventoryResponse> restock(
            @PathVariable UUID productId,
            @Valid @RequestBody RestockRequest request) {
        return ResponseEntity.ok(inventoryService.restockProduct(productId, request.quantity()));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Delete inventory record for a product")
    public ResponseEntity<Void> delete(@PathVariable UUID productId) {
        inventoryService.deleteByProductId(productId);
        return ResponseEntity.noContent().build();
    }
}
