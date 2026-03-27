package com.example.inventory.consumer;

import com.example.events.ProductDeletedEvent;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductDeletedConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "product-deleted", groupId = "inventory-service")
    public void onProductDeleted(ProductDeletedEvent event) {
        log.info("Product deleted event received: productId={}", event.getProductId());
        try {
            inventoryService.deleteByProductId(event.getProductId());
            log.info("Inventory record removed for productId={}", event.getProductId());
        } catch (InventoryNotFoundException e) {
            log.warn("No inventory found for deleted productId={}, skipping", event.getProductId());
        }
    }
}
