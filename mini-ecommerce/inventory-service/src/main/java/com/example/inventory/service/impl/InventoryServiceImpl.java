package com.example.inventory.service.impl;

import com.example.events.StockDepletedEvent;
import com.example.events.StockRestoredEvent;
import com.example.inventory.config.KafkaConfig;
import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.dto.response.InventoryResponse;
import com.example.inventory.entity.Inventory;
import com.example.inventory.exception.DuplicateInventoryException;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.mapper.InventoryMapper;
import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional(readOnly = true)
    public boolean checkAvailability(UUID productId, int quantity) {
        return inventoryRepository.findByProductId(productId)
                .map(inv -> inv.getQuantity() >= quantity)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getByProductId(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional
    public InventoryResponse create(CreateInventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.productId())) {
            throw new DuplicateInventoryException(request.productId());
        }
        Inventory inventory = new Inventory();
        inventory.setProductId(request.productId());
        inventory.setQuantity(request.quantity());
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Inventory created: productId={}, quantity={}", request.productId(), request.quantity());
        return inventoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void decreaseStock(UUID productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        if (inventory.getQuantity() < quantity) {
            throw new InsufficientStockException(productId, inventory.getQuantity(), quantity);
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
        log.info("Stock decreased: productId={}, remaining={}", productId, inventory.getQuantity());

        if (inventory.getQuantity() == 0) {
            kafkaTemplate.send(KafkaConfig.STOCK_DEPLETED_TOPIC,
                    StockDepletedEvent.builder()
                            .productId(productId)
                            .occurredAt(Instant.now())
                            .build());
            log.info("Stock depleted event published: productId={}", productId);
        }
    }

    @Override
    @Transactional
    public InventoryResponse restockProduct(UUID productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        boolean wasDepleted = inventory.getQuantity() == 0;

        inventory.setQuantity(inventory.getQuantity() + quantity);
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Stock restocked: productId={}, new quantity={}", productId, saved.getQuantity());

        if (wasDepleted) {
            kafkaTemplate.send(KafkaConfig.STOCK_RESTORED_TOPIC,
                    StockRestoredEvent.builder()
                            .productId(productId)
                            .newQuantity(saved.getQuantity())
                            .occurredAt(Instant.now())
                            .build());
            log.info("Stock restored event published: productId={}", productId);
        }

        return inventoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteByProductId(UUID productId) {
        if (!inventoryRepository.existsByProductId(productId)) {
            throw new InventoryNotFoundException(productId);
        }
        inventoryRepository.deleteByProductId(productId);
        log.info("Inventory deleted: productId={}", productId);
    }
}
