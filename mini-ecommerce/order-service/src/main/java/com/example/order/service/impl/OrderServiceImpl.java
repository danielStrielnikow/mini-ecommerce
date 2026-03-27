package com.example.order.service.impl;

import com.example.events.OrderCreatedEvent;
import com.example.order.client.InventoryClient;
import com.example.order.config.KafkaConfig;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.exception.InsufficientStockException;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.exception.OrderStatusTransitionException;
import com.example.order.mapper.OrderMapper;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderFilter;
import com.example.order.service.OrderService;
import com.example.order.service.OrderSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final int RESERVATION_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderMapper orderMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(OrderFilter filter, Pageable pageable) {
        return orderRepository.findAll(OrderSpecification.withFilter(filter), pageable)
                .map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

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
                .reservedUntil(Instant.now().plus(RESERVATION_MINUTES, ChronoUnit.MINUTES))
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
        log.info("Order created with reservation until {}: orderId={}", saved.getReservedUntil(), saved.getId());

        return orderMapper.toResponse(saved);
    }

    @Override
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order already cancelled (idempotent): orderId={}", orderId);
            return orderMapper.toResponse(order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        log.info("Order cancelled: orderId={}", orderId);
        return orderMapper.toResponse(saved);
    }

    @Override
    public OrderResponse confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order already confirmed (idempotent): orderId={}", orderId);
            return orderMapper.toResponse(order);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderStatusTransitionException(orderId, OrderStatus.CANCELLED, OrderStatus.CONFIRMED);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(order);
        log.info("Order confirmed: orderId={}", orderId);
        return orderMapper.toResponse(saved);
    }
}
