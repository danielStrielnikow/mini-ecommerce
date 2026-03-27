package com.example.order.service;

import com.example.events.OrderCreatedEvent;
import com.example.events.OrderExpiredEvent;
import com.example.order.client.InventoryClient;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.exception.InsufficientStockException;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.exception.OrderStatusTransitionException;
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
import java.util.Optional;
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
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private final UUID orderId   = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    // ── helpers ─────────────────────────────────────────────────────────────

    private Order orderWithStatus(OrderStatus status) {
        Order o = new Order();
        o.setId(orderId);
        o.setProductId(productId);
        o.setQuantity(2);
        o.setStatus(status);
        o.setTotalPrice(BigDecimal.ZERO);
        o.setCreatedAt(Instant.now());
        return o;
    }

    private OrderResponse responseWithStatus(OrderStatus status) {
        return new OrderResponse(orderId, productId, 2, status, BigDecimal.ZERO, Instant.now());
    }

    // ── createOrder ──────────────────────────────────────────────────────────

    @Test
    void createOrder_whenStockAvailable_shouldSaveOrderAndPublishEvent() {
        CreateOrderRequest request = new CreateOrderRequest(productId, 2);
        Order saved = orderWithStatus(OrderStatus.CREATED);
        OrderResponse expected = responseWithStatus(OrderStatus.CREATED);

        given(inventoryClient.checkAvailability(productId, 2)).willReturn(true);
        given(orderRepository.save(any(Order.class))).willReturn(saved);
        given(orderMapper.toResponse(saved)).willReturn(expected);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.productId()).isEqualTo(productId);

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        then(kafkaTemplate).should().send(eq("order-created"), captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(productId);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
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

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_whenExists_shouldReturnOrderResponse() {
        Order order = orderWithStatus(OrderStatus.CREATED);
        OrderResponse expected = responseWithStatus(OrderStatus.CREATED);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderMapper.toResponse(order)).willReturn(expected);

        OrderResponse result = orderService.getById(orderId);

        assertThat(result.id()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void getById_whenNotFound_shouldThrowOrderNotFoundException() {
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    // ── cancelOrder ──────────────────────────────────────────────────────────

    @Test
    void cancelOrder_whenCreated_shouldSetCancelledAndPublishStockRestoreEvent() {
        Order order = orderWithStatus(OrderStatus.CREATED);
        Order saved = orderWithStatus(OrderStatus.CANCELLED);
        OrderResponse expected = responseWithStatus(OrderStatus.CANCELLED);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(order)).willReturn(saved);
        given(orderMapper.toResponse(saved)).willReturn(expected);

        OrderResponse result = orderService.cancelOrder(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<OrderExpiredEvent> captor = ArgumentCaptor.forClass(OrderExpiredEvent.class);
        then(kafkaTemplate).should().send(eq("order-expired"), captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(productId);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    void cancelOrder_whenAlreadyCancelled_shouldBeIdempotent() {
        Order order = orderWithStatus(OrderStatus.CANCELLED);
        OrderResponse expected = responseWithStatus(OrderStatus.CANCELLED);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderMapper.toResponse(order)).willReturn(expected);

        OrderResponse result = orderService.cancelOrder(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        then(orderRepository).should(never()).save(any());
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    void cancelOrder_whenNotFound_shouldThrowOrderNotFoundException() {
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    void cancelOrderByEvent_whenCreated_shouldSetCancelledWithoutPublishingEvent() {
        Order order = orderWithStatus(OrderStatus.CREATED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(order)).willReturn(orderWithStatus(OrderStatus.CANCELLED));

        orderService.cancelOrderByEvent(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    void cancelOrderByEvent_whenAlreadyCancelled_shouldBeIdempotent() {
        Order order = orderWithStatus(OrderStatus.CANCELLED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        orderService.cancelOrderByEvent(orderId);

        then(orderRepository).should(never()).save(any());
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    // ── confirmOrder ─────────────────────────────────────────────────────────

    @Test
    void confirmOrder_whenCreated_shouldSetConfirmedAndReturn() {
        Order order = orderWithStatus(OrderStatus.CREATED);
        Order saved = orderWithStatus(OrderStatus.CONFIRMED);
        OrderResponse expected = responseWithStatus(OrderStatus.CONFIRMED);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(order)).willReturn(saved);
        given(orderMapper.toResponse(saved)).willReturn(expected);

        OrderResponse result = orderService.confirmOrder(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirmOrder_whenAlreadyConfirmed_shouldBeIdempotent() {
        Order order = orderWithStatus(OrderStatus.CONFIRMED);
        OrderResponse expected = responseWithStatus(OrderStatus.CONFIRMED);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderMapper.toResponse(order)).willReturn(expected);

        OrderResponse result = orderService.confirmOrder(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        then(orderRepository).should(never()).save(any());
    }

    @Test
    void confirmOrder_whenCancelled_shouldThrowOrderStatusTransitionException() {
        Order order = orderWithStatus(OrderStatus.CANCELLED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmOrder(orderId))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("CANCELLED")
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void confirmOrder_whenNotFound_shouldThrowOrderNotFoundException() {
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }
}
