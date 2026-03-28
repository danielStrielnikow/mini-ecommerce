package com.example.inventory.service.impl;

import com.example.events.StockDepletedEvent;
import com.example.events.StockRestoredEvent;
import com.example.inventory.config.KafkaConfig;
import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.dto.response.InventoryResponse;
import com.example.inventory.dto.response.InventorySummaryResponse;
import com.example.inventory.entity.Inventory;
import com.example.inventory.exception.DuplicateInventoryException;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.mapper.InventoryMapper;
import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.service.InventoryFilter;
import com.example.inventory.service.InventoryService;
import com.example.inventory.service.InventorySpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    public Page<InventorySummaryResponse> findAll(InventoryFilter filter, Pageable pageable) {
        return inventoryRepository
                .findAll(InventorySpecification.withFilter(filter), pageable)
                .map(inventoryMapper::toSummary);
    }

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
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        kafkaTemplate.send(KafkaConfig.STOCK_DEPLETED_TOPIC,
                                StockDepletedEvent.builder()
                                        .productId(productId)
                                        .occurredAt(Instant.now())
                                        .build());
                        log.info("Stock depleted event published: productId={}", productId);
                    } catch (RuntimeException e) {
                        log.warn("Failed to publish StockDepletedEvent for productId={}: {}", productId, e.getMessage());
                    }
                }
            });
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
            int newQuantity = saved.getQuantity();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        kafkaTemplate.send(KafkaConfig.STOCK_RESTORED_TOPIC,
                                StockRestoredEvent.builder()
                                        .productId(productId)
                                        .newQuantity(newQuantity)
                                        .occurredAt(Instant.now())
                                        .build());
                        log.info("Stock restored event published: productId={}", productId);
                    } catch (RuntimeException e) {
                        log.warn("Failed to publish StockRestoredEvent for productId={}: {}", productId, e.getMessage());
                    }
                }
            });
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
