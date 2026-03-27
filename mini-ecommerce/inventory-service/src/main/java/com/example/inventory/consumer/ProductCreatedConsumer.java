package com.example.inventory.consumer;

import com.example.events.ProductCreatedEvent;
import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.exception.DuplicateInventoryException;
import com.example.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCreatedConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "product-created", groupId = "inventory-service")
    public void onProductCreated(ProductCreatedEvent event) {
        log.info("Product created event received: productId={}", event.getProductId());
        try {
            inventoryService.create(new CreateInventoryRequest(event.getProductId(), 0));
            log.info("Inventory record auto-created for productId={}", event.getProductId());
        } catch (DuplicateInventoryException e) {
            log.warn("Inventory already exists for productId={}, skipping", event.getProductId());
        }
    }
}
