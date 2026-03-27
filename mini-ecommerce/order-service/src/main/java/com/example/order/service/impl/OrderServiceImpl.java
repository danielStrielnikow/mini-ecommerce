package com.example.order.service.impl;

import com.example.events.OrderCreatedEvent;
import com.example.order.client.InventoryClient;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.config.KafkaConfig;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.exception.InsufficientStockException;
import com.example.order.mapper.OrderMapper;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final OrderMapper orderMapper;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        boolean available = inventoryClient.checkAvailability(request.productId(), request.quantity());

        if (!available) {
            throw new InsufficientStockException(request.productId(), request.quantity());
        }

        Order order = Order.builder()
                .productId(request.productId())
                .quantity(request.quantity())
                .status(OrderStatus.CREATED)
                .totalPrice(BigDecimal.ZERO)
                .build();

        Order saved = orderRepository.save(order);

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(saved.getId())
                .productId(saved.getProductId())
                .quantity(saved.getQuantity())
                .totalPrice(saved.getTotalPrice())
                .createdAt(Instant.now())
                .build();

        kafkaTemplate.send(KafkaConfig.ORDER_CREATED_TOPIC, event);
        log.info("Order created and event published: orderId={}", saved.getId());

        return orderMapper.toResponse(saved);
    }

    @Override
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order cancelled: orderId={}", orderId);
    }
}
