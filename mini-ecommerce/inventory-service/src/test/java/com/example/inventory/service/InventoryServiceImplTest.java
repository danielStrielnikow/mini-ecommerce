package com.example.inventory.service;

import com.example.inventory.entity.Inventory;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private final UUID productId = UUID.randomUUID();

    @Test
    void checkAvailability_whenSufficientStock_shouldReturnTrue() {
        Inventory inventory = buildInventory(10);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        assertThat(inventoryService.checkAvailability(productId, 5)).isTrue();
    }

    @Test
    void checkAvailability_whenInsufficientStock_shouldReturnFalse() {
        Inventory inventory = buildInventory(2);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        assertThat(inventoryService.checkAvailability(productId, 5)).isFalse();
    }

    @Test
    void checkAvailability_whenProductNotFound_shouldReturnFalse() {
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.empty());

        assertThat(inventoryService.checkAvailability(productId, 1)).isFalse();
    }

    @Test
    void decreaseStock_whenSufficientStock_shouldDecreaseQuantity() {
        Inventory inventory = buildInventory(10);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        inventoryService.decreaseStock(productId, 3);

        assertThat(inventory.getQuantity()).isEqualTo(7);
        then(inventoryRepository).should().save(inventory);
    }

    @Test
    void decreaseStock_whenInventoryNotFound_shouldThrow() {
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.decreaseStock(productId, 1))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    private Inventory buildInventory(int quantity) {
        Inventory inv = new Inventory();
        inv.setProductId(productId);
        inv.setQuantity(quantity);
        return inv;
    }
}
