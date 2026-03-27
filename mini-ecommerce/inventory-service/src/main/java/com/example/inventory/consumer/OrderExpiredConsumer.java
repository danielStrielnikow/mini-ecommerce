package com.example.inventory.consumer;

import com.example.events.OrderExpiredEvent;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiredConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-expired", groupId = "inventory-service")
    public void onOrderExpired(OrderExpiredEvent event) {
        log.info("Order expired event received: orderId={}, productId={}, quantity={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());
        try {
            inventoryService.restockProduct(event.getProductId(), event.getQuantity());
            log.info("Stock restored after reservation expiry: productId={}", event.getProductId());
        } catch (InventoryNotFoundException e) {
            log.warn("Inventory not found when restoring expired reservation: productId={}", event.getProductId());
        }
    }
}
