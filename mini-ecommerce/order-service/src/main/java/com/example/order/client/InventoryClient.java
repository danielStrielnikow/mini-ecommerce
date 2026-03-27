package com.example.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryClient {

    private final RestClient restClient;

    @Value("${app.inventory.base-url}")
    private String inventoryBaseUrl;

    @CircuitBreaker(name = "inventory", fallbackMethod = "checkAvailabilityFallback")
    public boolean checkAvailability(UUID productId, int quantity) {
        Boolean result = restClient.get()
                .uri(inventoryBaseUrl + "/api/inventory/check?productId={productId}&quantity={quantity}",
                        productId, quantity)
                .retrieve()
                .body(Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private boolean checkAvailabilityFallback(UUID productId, int quantity, Throwable ex) {
        log.warn("Inventory service unavailable for productId={}, quantity={}. Cause: {}",
                productId, quantity, ex.getMessage());
        return false;
    }
}
