package com.example.order.controller;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.exception.GlobalExceptionHandler;
import com.example.order.exception.InsufficientStockException;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private final UUID productId = UUID.randomUUID();

    @Test
    void createOrder_whenValid_shouldReturn201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(productId, 2);
        OrderResponse response = new OrderResponse(UUID.randomUUID(), productId, 2,
                OrderStatus.CREATED, BigDecimal.ZERO, Instant.now());

        given(orderService.createOrder(any())).willReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.productId").value(productId.toString()));
    }

    @Test
    void createOrder_whenInsufficientStock_shouldReturn409() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(productId, 99);
        given(orderService.createOrder(any())).willThrow(new InsufficientStockException(productId, 99));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createOrder_whenQuantityIsZero_shouldReturn400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(productId, 0);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_whenProductIdIsNull_shouldReturn400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(null, 2);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
