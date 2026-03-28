package com.example.order.controller;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.dto.response.OrderSummaryResponse;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.exception.GlobalExceptionHandler;
import com.example.order.exception.InsufficientStockException;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.exception.OrderStatusTransitionException;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    private final UUID orderId   = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private OrderResponse sampleResponse(OrderStatus status) {
        return new OrderResponse(orderId, productId, 2, status, new BigDecimal("199.98"),
                Instant.now(), Instant.now().plusSeconds(900), null);
    }

    // ── POST /api/orders ─────────────────────────────────────────────────────

    @Test
    void createOrder_whenValid_shouldReturn201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(productId, 2);
        given(orderService.createOrder(any())).willReturn(sampleResponse(OrderStatus.CREATED));

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

    // ── GET /api/orders ──────────────────────────────────────────────────────

    private OrderSummaryResponse sampleSummary(OrderStatus status) {
        return new OrderSummaryResponse(orderId, productId, 2, status, new BigDecimal("199.98"),
                Instant.now(), Instant.now().plusSeconds(900), null);
    }

    @Test
    void findAll_shouldReturn200WithPage() throws Exception {
        given(orderService.findAll(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(sampleSummary(OrderStatus.CREATED))));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CREATED"));
    }

    @Test
    void findAll_withStatusFilter_shouldReturn200() throws Exception {
        given(orderService.findAll(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(sampleSummary(OrderStatus.CONFIRMED))));

        mockMvc.perform(get("/api/orders").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    // ── GET /api/orders/{id} ─────────────────────────────────────────────────

    @Test
    void getById_whenExists_shouldReturn200() throws Exception {
        given(orderService.getById(orderId)).willReturn(sampleResponse(OrderStatus.CREATED));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    void getById_whenNotFound_shouldReturn404() throws Exception {
        given(orderService.getById(orderId)).willThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/orders/{id}/cancel ────────────────────────────────────────

    @Test
    void cancel_whenValid_shouldReturn200WithCancelledStatus() throws Exception {
        given(orderService.cancelOrder(orderId)).willReturn(sampleResponse(OrderStatus.CANCELLED));

        mockMvc.perform(patch("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_whenNotFound_shouldReturn404() throws Exception {
        given(orderService.cancelOrder(orderId)).willThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(patch("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/orders/{id}/confirm ───────────────────────────────────────

    @Test
    void confirm_whenValid_shouldReturn200WithConfirmedStatus() throws Exception {
        given(orderService.confirmOrder(orderId)).willReturn(sampleResponse(OrderStatus.CONFIRMED));

        mockMvc.perform(patch("/api/orders/{id}/confirm", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirm_whenCancelled_shouldReturn409() throws Exception {
        given(orderService.confirmOrder(orderId))
                .willThrow(new OrderStatusTransitionException(orderId, OrderStatus.CANCELLED, OrderStatus.CONFIRMED));

        mockMvc.perform(patch("/api/orders/{id}/confirm", orderId))
                .andExpect(status().isConflict());
    }

    @Test
    void confirm_whenNotFound_shouldReturn404() throws Exception {
        given(orderService.confirmOrder(orderId)).willThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(patch("/api/orders/{id}/confirm", orderId))
                .andExpect(status().isNotFound());
    }
}
