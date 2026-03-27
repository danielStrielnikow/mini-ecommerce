package com.example.inventory.controller;

import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.dto.request.RestockRequest;
import com.example.inventory.dto.response.InventoryResponse;
import com.example.inventory.exception.DuplicateInventoryException;
import com.example.inventory.exception.GlobalExceptionHandler;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private InventoryService inventoryService;

    private final UUID productId = UUID.randomUUID();

    // ── check ────────────────────────────────────────────────────────────────

    @Test
    void check_whenAvailable_shouldReturnTrue() throws Exception {
        given(inventoryService.checkAvailability(productId, 2)).willReturn(true);

        mockMvc.perform(get("/api/inventory/check")
                        .param("productId", productId.toString())
                        .param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void check_whenNotAvailable_shouldReturnFalse() throws Exception {
        given(inventoryService.checkAvailability(productId, 99)).willReturn(false);

        mockMvc.perform(get("/api/inventory/check")
                        .param("productId", productId.toString())
                        .param("quantity", "99"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    // ── getByProductId ───────────────────────────────────────────────────────

    @Test
    void getByProductId_whenExists_shouldReturn200() throws Exception {
        InventoryResponse response = new InventoryResponse(productId, 10, true);
        given(inventoryService.getByProductId(productId)).willReturn(response);

        mockMvc.perform(get("/api/inventory/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void getByProductId_whenNotFound_shouldReturn404() throws Exception {
        given(inventoryService.getByProductId(productId)).willThrow(new InventoryNotFoundException(productId));

        mockMvc.perform(get("/api/inventory/{productId}", productId))
                .andExpect(status().isNotFound());
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_shouldReturn201() throws Exception {
        CreateInventoryRequest request = new CreateInventoryRequest(productId, 50);
        InventoryResponse response = new InventoryResponse(productId, 50, true);
        given(inventoryService.create(any())).willReturn(response);

        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    void create_whenDuplicate_shouldReturn409() throws Exception {
        CreateInventoryRequest request = new CreateInventoryRequest(productId, 50);
        given(inventoryService.create(any())).willThrow(new DuplicateInventoryException(productId));

        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_whenMissingProductId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 10}"))
                .andExpect(status().isBadRequest());
    }

    // ── restock ──────────────────────────────────────────────────────────────

    @Test
    void restock_shouldReturn200WithUpdatedQuantity() throws Exception {
        RestockRequest request = new RestockRequest(30);
        InventoryResponse response = new InventoryResponse(productId, 30, true);
        given(inventoryService.restockProduct(eq(productId), eq(30))).willReturn(response);

        mockMvc.perform(patch("/api/inventory/{productId}/restock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(30));
    }

    @Test
    void restock_whenNotFound_shouldReturn404() throws Exception {
        given(inventoryService.restockProduct(any(), anyInt()))
                .willThrow(new InventoryNotFoundException(productId));

        mockMvc.perform(patch("/api/inventory/{productId}/restock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5}"))
                .andExpect(status().isNotFound());
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_whenExists_shouldReturn204() throws Exception {
        willDoNothing().given(inventoryService).deleteByProductId(productId);

        mockMvc.perform(delete("/api/inventory/{productId}", productId))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenNotFound_shouldReturn404() throws Exception {
        willThrow(new InventoryNotFoundException(productId))
                .given(inventoryService).deleteByProductId(productId);

        mockMvc.perform(delete("/api/inventory/{productId}", productId))
                .andExpect(status().isNotFound());
    }
}
