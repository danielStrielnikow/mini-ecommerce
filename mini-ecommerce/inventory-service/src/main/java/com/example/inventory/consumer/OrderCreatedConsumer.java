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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedConsumer {

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private void sendCancellation(UUID orderId, UUID productId, String reason) {
        try {
            kafkaTemplate.send(KafkaConfig.ORDER_CANCELLED_TOPIC,
                    OrderCancelledEvent.builder()
                            .orderId(orderId)
                            .productId(productId)
                            .reason(reason)
                            .occurredAt(Instant.now())
                            .build());
            log.info("Order cancellation event published: orderId={}, reason={}", orderId, reason);
        } catch (RuntimeException e) {
            // fire-and-forget: if this fails, order remains CREATED in order-service
            // but won't be confirmed — will eventually be expired by ReservationExpiryScheduler
            log.error("Failed to publish OrderCancelledEvent for orderId={}: {}", orderId, e.getMessage());
        }
    }

    @KafkaListener(topics = "order-created", groupId = "inventory-service")
    public void consume(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent: orderId={}, productId={}, quantity={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());
        try {
            inventoryService.decreaseStock(event.getProductId(), event.getQuantity());
        } catch (InsufficientStockException e) {
            log.warn("Insufficient stock for orderId={}: {}", event.getOrderId(), e.getMessage());
            sendCancellation(event.getOrderId(), event.getProductId(), "Insufficient stock");
        } catch (InventoryNotFoundException e) {
            log.warn("Inventory not found for orderId={}, productId={} — skipping",
                    event.getOrderId(), event.getProductId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent modification detected for orderId={}, productId={} — cancelling order",
                    event.getOrderId(), event.getProductId());
            sendCancellation(event.getOrderId(), event.getProductId(),
                    "Concurrent modification — stock no longer available");
        } catch (Exception e) {
            log.error("Unexpected error processing OrderCreatedEvent for orderId={}: {}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
