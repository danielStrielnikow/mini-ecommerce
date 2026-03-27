package com.example.inventory.controller;

import com.example.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    private final UUID productId = UUID.randomUUID();

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
}
