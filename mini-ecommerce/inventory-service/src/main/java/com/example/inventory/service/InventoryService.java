package com.example.inventory.service;

import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.dto.response.InventoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryService {

    Page<InventoryResponse> findAll(InventoryFilter filter, Pageable pageable);

    boolean checkAvailability(UUID productId, int quantity);

    InventoryResponse getByProductId(UUID productId);

    InventoryResponse create(CreateInventoryRequest request);

    void decreaseStock(UUID productId, int quantity);

    InventoryResponse restockProduct(UUID productId, int quantity);

    void deleteByProductId(UUID productId);
}
