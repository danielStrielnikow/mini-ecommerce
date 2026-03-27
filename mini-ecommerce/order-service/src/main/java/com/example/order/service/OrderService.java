package com.example.order.service;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.dto.response.OrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    Page<OrderSummaryResponse> findAll(OrderFilter filter, Pageable pageable);

    OrderResponse getById(UUID orderId);

    OrderResponse createOrder(CreateOrderRequest request);

    // Called from HTTP endpoint — cancels order and publishes event to restore stock
    OrderResponse cancelOrder(UUID orderId);

    // Called from Kafka consumers — only sets CANCELLED status, stock was never decreased
    void cancelOrderByEvent(UUID orderId);

    OrderResponse confirmOrder(UUID orderId);
}
