package com.example.inventory.consumer;

import com.example.events.OrderCancelledEvent;
import com.example.events.OrderCreatedEvent;
import com.example.inventory.config.KafkaConfig;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedConsumer {

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "order-created", groupId = "inventory-service")
    public void consume(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent: orderId={}, productId={}, quantity={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());
        try {
            inventoryService.decreaseStock(event.getProductId(), event.getQuantity());
        } catch (InsufficientStockException e) {
            log.warn("Insufficient stock for orderId={}: {}", event.getOrderId(), e.getMessage());
            kafkaTemplate.send(KafkaConfig.ORDER_CANCELLED_TOPIC,
                    OrderCancelledEvent.builder()
                            .orderId(event.getOrderId())
                            .productId(event.getProductId())
                            .reason("Insufficient stock")
                            .occurredAt(Instant.now())
                            .build());
        } catch (InventoryNotFoundException e) {
            log.warn("Inventory not found for orderId={}, productId={} — skipping",
                    event.getOrderId(), event.getProductId());
        }
    }
}
