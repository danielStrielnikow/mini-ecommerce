package com.example.product.consumer;

import com.example.events.StockDepletedEvent;
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
public class StockDepletedConsumer {

    private final ProductService productService;

    @KafkaListener(topics = KafkaConfig.STOCK_DEPLETED_TOPIC, groupId = "product-service")
    public void onStockDepleted(StockDepletedEvent event) {
        log.info("Stock depleted event received for productId={}", event.getProductId());
        try {
            productService.deactivate(event.getProductId());
            log.info("Product deactivated due to stock depletion: productId={}", event.getProductId());
        } catch (ProductNotFoundException e) {
            log.warn("Product not found during deactivation: productId={}", event.getProductId());
        }
    }
}
