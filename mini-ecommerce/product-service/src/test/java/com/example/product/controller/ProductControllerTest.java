package com.example.product.controller;

import com.example.product.dto.response.ProductResponse;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    private final UUID productId = UUID.randomUUID();

    private ProductResponse sampleResponse() {
        return new ProductResponse(productId, "Laptop Pro", "High-end laptop",
                new BigDecimal("4999.99"), Instant.now());
    }

    @Test
    void getAll_shouldReturn200WithProductList() throws Exception {
        given(productService.findAll()).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/products").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Laptop Pro"))
                .andExpect(jsonPath("$[0].price").value(4999.99));
    }

    @Test
    void getAll_whenEmpty_shouldReturn200WithEmptyArray() throws Exception {
        given(productService.findAll()).willReturn(List.of());

        mockMvc.perform(get("/api/products").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getById_whenExists_shouldReturn200() throws Exception {
        given(productService.findById(productId)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/products/{id}", productId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.name").value("Laptop Pro"));
    }

    @Test
    void getById_whenNotFound_shouldReturn404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(productService.findById(unknownId)).willThrow(new ProductNotFoundException(unknownId));

        mockMvc.perform(get("/api/products/{id}", unknownId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
