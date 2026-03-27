package com.example.inventory.service;

import com.example.events.StockDepletedEvent;
import com.example.events.StockRestoredEvent;
import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.dto.response.InventoryResponse;
import com.example.inventory.entity.Inventory;
import com.example.inventory.exception.DuplicateInventoryException;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.mapper.InventoryMapper;
import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private InventoryMapper inventoryMapper;

    @InjectMocks private InventoryServiceImpl inventoryService;

    private final UUID productId = UUID.randomUUID();

    // ── checkAvailability ────────────────────────────────────────────────────

    @Test
    void checkAvailability_whenSufficientStock_shouldReturnTrue() {
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(buildInventory(10)));

        assertThat(inventoryService.checkAvailability(productId, 5)).isTrue();
    }

    @Test
    void checkAvailability_whenInsufficientStock_shouldReturnFalse() {
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(buildInventory(2)));

        assertThat(inventoryService.checkAvailability(productId, 5)).isFalse();
    }

    @Test
    void checkAvailability_whenProductNotFound_shouldReturnFalse() {
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.empty());

        assertThat(inventoryService.checkAvailability(productId, 1)).isFalse();
    }

    // ── getByProductId ───────────────────────────────────────────────────────

    @Test
    void getByProductId_whenExists_shouldReturnResponse() {
        Inventory inventory = buildInventory(10);
        InventoryResponse expected = new InventoryResponse(UUID.randomUUID(), productId, 10, true);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));
        given(inventoryMapper.toResponse(inventory)).willReturn(expected);

        InventoryResponse result = inventoryService.getByProductId(productId);

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.quantity()).isEqualTo(10);
        assertThat(result.available()).isTrue();
    }

    @Test
    void getByProductId_whenNotFound_shouldThrow() {
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getByProductId(productId))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_shouldSaveAndReturnResponse() {
        CreateInventoryRequest request = new CreateInventoryRequest(productId, 100);
        Inventory saved = buildInventory(100);
        InventoryResponse expected = new InventoryResponse(UUID.randomUUID(), productId, 100, true);
        given(inventoryRepository.existsByProductId(productId)).willReturn(false);
        given(inventoryRepository.save(any())).willReturn(saved);
        given(inventoryMapper.toResponse(saved)).willReturn(expected);

        InventoryResponse result = inventoryService.create(request);

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.quantity()).isEqualTo(100);
        then(inventoryRepository).should().save(any());
    }

    @Test
    void create_whenAlreadyExists_shouldThrowDuplicateInventoryException() {
        CreateInventoryRequest request = new CreateInventoryRequest(productId, 100);
        given(inventoryRepository.existsByProductId(productId)).willReturn(true);

        assertThatThrownBy(() -> inventoryService.create(request))
                .isInstanceOf(DuplicateInventoryException.class);
        then(inventoryRepository).should(never()).save(any());
    }

    // ── decreaseStock ────────────────────────────────────────────────────────

    @Test
    void decreaseStock_whenSufficientStock_shouldDecreaseQuantity() {
        Inventory inventory = buildInventory(10);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        inventoryService.decreaseStock(productId, 3);

        assertThat(inventory.getQuantity()).isEqualTo(7);
        then(inventoryRepository).should().save(inventory);
    }

    @Test
    void decreaseStock_whenStockHitsZero_shouldPublishStockDepletedEvent() {
        Inventory inventory = buildInventory(3);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        inventoryService.decreaseStock(productId, 3);

        assertThat(inventory.getQuantity()).isZero();
        then(kafkaTemplate).should().send(eq("stock-depleted"), any(StockDepletedEvent.class));
    }

    @Test
    void decreaseStock_whenStockAboveZero_shouldNotPublishDepletedEvent() {
        Inventory inventory = buildInventory(10);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        inventoryService.decreaseStock(productId, 3);

        then(kafkaTemplate).should(never()).send(eq("stock-depleted"), any());
    }

    @Test
    void decreaseStock_whenInsufficientStock_shouldThrowInsufficientStockException() {
        Inventory inventory = buildInventory(2);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.decreaseStock(productId, 5))
                .isInstanceOf(InsufficientStockException.class);
        then(inventoryRepository).should(never()).save(any());
    }

    @Test
    void decreaseStock_whenInventoryNotFound_shouldThrow() {
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.decreaseStock(productId, 1))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    // ── restock ──────────────────────────────────────────────────────────────

    @Test
    void restock_whenWasDepleted_shouldPublishStockRestoredAndReturnResponse() {
        Inventory inventory = buildInventory(0);
        InventoryResponse expected = new InventoryResponse(UUID.randomUUID(), productId, 50, true);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(inventory)).willReturn(inventory);
        given(inventoryMapper.toResponse(inventory)).willReturn(expected);

        InventoryResponse result = inventoryService.restockProduct(productId, 50);

        assertThat(inventory.getQuantity()).isEqualTo(50);
        assertThat(result.quantity()).isEqualTo(50);
        assertThat(result.available()).isTrue();
        then(kafkaTemplate).should().send(eq("stock-restored"), any(StockRestoredEvent.class));
    }

    @Test
    void restock_whenAlreadyHadStock_shouldNotPublishRestoredEvent() {
        Inventory inventory = buildInventory(10);
        InventoryResponse expected = new InventoryResponse(UUID.randomUUID(), productId, 60, true);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(inventory)).willReturn(inventory);
        given(inventoryMapper.toResponse(inventory)).willReturn(expected);

        InventoryResponse result = inventoryService.restockProduct(productId, 50);

        assertThat(inventory.getQuantity()).isEqualTo(60);
        assertThat(result.quantity()).isEqualTo(60);
        then(kafkaTemplate).should(never()).send(eq("stock-restored"), any());
    }

    @Test
    void restock_whenInventoryNotFound_shouldThrow() {
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.restockProduct(productId, 10))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_whenExists_shouldRemoveRecord() {
        given(inventoryRepository.existsByProductId(productId)).willReturn(true);

        inventoryService.deleteByProductId(productId);

        then(inventoryRepository).should().deleteByProductId(productId);
    }

    @Test
    void delete_whenNotFound_shouldThrow() {
        given(inventoryRepository.existsByProductId(productId)).willReturn(false);

        assertThatThrownBy(() -> inventoryService.deleteByProductId(productId))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private Inventory buildInventory(int quantity) {
        Inventory inv = new Inventory();
        inv.setProductId(productId);
        inv.setQuantity(quantity);
        return inv;
    }
}
