package com.example.order.controller;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.dto.response.OrderSummaryResponse;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.service.OrderFilter;
import com.example.order.service.OrderService;
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
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management API")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List orders with optional filters")
    @ApiResponse(responseCode = "200", description = "Orders returned successfully")
    public ResponseEntity<Page<OrderSummaryResponse>> findAll(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID productId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        OrderFilter filter = new OrderFilter(status, productId);
        return ResponseEntity.ok(orderService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new order",
               description = "Rate limited to 10 requests per minute. Synchronously validates product and stock, then reserves inventory for 15 minutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created and inventory reserved"),
            @ApiResponse(responseCode = "400", description = "Validation failed — quantity must be at least 1"),
            @ApiResponse(responseCode = "404", description = "Product not found or inactive"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded — max 10 orders per minute"),
            @ApiResponse(responseCode = "503", description = "Inventory or product service unavailable (circuit breaker open)")
    })
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order", description = "Cancels order and publishes event to restore inventory stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order is already confirmed and cannot be cancelled")
    })
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirm an order", description = "Confirms a CREATED order. Idempotent if already CONFIRMED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order confirmed"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order is cancelled and cannot be confirmed")
    })
    public ResponseEntity<OrderResponse> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.confirmOrder(id));
    }
}
