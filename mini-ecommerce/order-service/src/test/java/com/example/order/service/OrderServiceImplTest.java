package com.example.order.service;

import com.example.events.OrderCreatedEvent;
import com.example.order.client.InventoryClient;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.exception.InsufficientStockException;
import com.example.order.mapper.OrderMapper;
import com.example.order.repository.OrderRepository;
import com.example.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private final UUID productId = UUID.randomUUID();

    @Test
    void createOrder_whenStockAvailable_shouldSaveOrderAndPublishEvent() {
        CreateOrderRequest request = new CreateOrderRequest(productId, 2);

        Order savedOrder = new Order();
        savedOrder.setId(UUID.randomUUID());
        savedOrder.setProductId(productId);
        savedOrder.setQuantity(2);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setTotalPrice(BigDecimal.ZERO);
        savedOrder.setCreatedAt(Instant.now());

        OrderResponse expectedResponse = new OrderResponse(
                savedOrder.getId(), productId, 2, OrderStatus.CREATED, BigDecimal.ZERO, savedOrder.getCreatedAt());

        given(inventoryClient.checkAvailability(productId, 2)).willReturn(true);
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);
        given(orderMapper.toResponse(savedOrder)).willReturn(expectedResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.quantity()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);

        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        then(kafkaTemplate).should().send(eq("order-created"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getProductId()).isEqualTo(productId);
        assertThat(eventCaptor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    void createOrder_whenStockUnavailable_shouldThrowInsufficientStockException() {
        CreateOrderRequest request = new CreateOrderRequest(productId, 99);
        given(inventoryClient.checkAvailability(productId, 99)).willReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining(productId.toString());

        then(orderRepository).shouldHaveNoInteractions();
        then(kafkaTemplate).shouldHaveNoInteractions();
    }
}
