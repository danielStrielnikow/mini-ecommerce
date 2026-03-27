package com.example.inventory.controller;

import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.dto.request.RestockRequest;
import com.example.inventory.dto.response.InventoryResponse;
import com.example.inventory.dto.response.InventorySummaryResponse;
import com.example.inventory.service.InventoryFilter;
import com.example.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponse(responseCode = "200", description = "Inventory records returned successfully")
    public ResponseEntity<Page<InventorySummaryResponse>> findAll(
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Integer minQuantity,
            @RequestParam(required = false) Integer maxQuantity,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        InventoryFilter filter = new InventoryFilter(available, minQuantity, maxQuantity);
        return ResponseEntity.ok(inventoryService.findAll(filter, pageable));
    }

    @GetMapping("/check")
    @Operation(summary = "Check product availability", description = "Returns true if requested quantity is available. Returns false (not 404) if product has no inventory record.")
    @ApiResponse(responseCode = "200", description = "Returns true if stock is sufficient, false otherwise")
    public ResponseEntity<Boolean> check(
            @RequestParam UUID productId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(inventoryService.checkAvailability(productId, quantity));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory by product ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory record found"),
            @ApiResponse(responseCode = "404", description = "Inventory record not found for this product")
    })
    public ResponseEntity<InventoryResponse> getByProductId(@PathVariable UUID productId) {
        return ResponseEntity.ok(inventoryService.getByProductId(productId));
    }

    @PostMapping
    @Operation(summary = "Create inventory record for a product")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inventory record created"),
            @ApiResponse(responseCode = "400", description = "Validation failed — quantity must be non-negative"),
            @ApiResponse(responseCode = "409", description = "Inventory record already exists for this product")
    })
    public ResponseEntity<InventoryResponse> create(@Valid @RequestBody CreateInventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.create(request));
    }

    @PatchMapping("/{productId}/restock")
    @Operation(summary = "Restock product inventory")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory restocked"),
            @ApiResponse(responseCode = "400", description = "Validation failed — quantity must be at least 1"),
            @ApiResponse(responseCode = "404", description = "Inventory record not found for this product")
    })
    public ResponseEntity<InventoryResponse> restock(
            @PathVariable UUID productId,
            @Valid @RequestBody RestockRequest request) {
        return ResponseEntity.ok(inventoryService.restockProduct(productId, request.quantity()));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Delete inventory record for a product")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inventory record deleted"),
            @ApiResponse(responseCode = "404", description = "Inventory record not found for this product")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID productId) {
        inventoryService.deleteByProductId(productId);
        return ResponseEntity.noContent().build();
    }
}
