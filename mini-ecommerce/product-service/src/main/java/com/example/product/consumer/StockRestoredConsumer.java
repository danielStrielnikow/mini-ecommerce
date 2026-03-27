package com.example.product.consumer;

import com.example.events.StockRestoredEvent;
import com.example.product.config.KafkaConfig;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockRestoredConsumer {

    private final ProductService productService;

    @KafkaListener(topics = KafkaConfig.STOCK_RESTORED_TOPIC, groupId = "product-service")
    public void onStockRestored(StockRestoredEvent event) {
        log.info("Stock restored event received for productId={}, qty={}",
                event.getProductId(), event.getNewQuantity());
        try {
            productService.activate(event.getProductId());
            log.info("Product activated after restock: productId={}", event.getProductId());
        } catch (ProductNotFoundException e) {
            log.warn("Product not found during activation: productId={}", event.getProductId());
        }
    }
}
