package com.example.inventory.consumer;

import com.example.events.OrderCreatedEvent;
import com.example.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-created", groupId = "inventory-service")
    public void consume(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent: orderId={}, productId={}, quantity={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());

        inventoryService.decreaseStock(event.getProductId(), event.getQuantity());
    }
}
