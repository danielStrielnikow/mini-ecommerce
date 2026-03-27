package com.example.order.service;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    Page<OrderResponse> findAll(OrderFilter filter, Pageable pageable);

    OrderResponse getById(UUID orderId);

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse cancelOrder(UUID orderId);

    OrderResponse confirmOrder(UUID orderId);
}
