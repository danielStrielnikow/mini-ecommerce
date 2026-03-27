package com.example.order.scheduler;

import com.example.events.OrderExpiredEvent;
import com.example.order.entity.Order;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationExpirySchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ReservationExpiryScheduler scheduler;

    @Test
    void expireReservations_whenExpiredOrdersExist_shouldCancelAndPublishEvent() {
        UUID orderId   = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order expiredOrder = new Order();
        expiredOrder.setId(orderId);
        expiredOrder.setProductId(productId);
        expiredOrder.setQuantity(3);
        expiredOrder.setStatus(OrderStatus.CREATED);
        expiredOrder.setTotalPrice(BigDecimal.ZERO);
        expiredOrder.setReservedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));

        given(orderRepository.findAllByStatusAndReservedUntilBefore(eq(OrderStatus.CREATED), any(Instant.class)))
                .willReturn(List.of(expiredOrder));
        given(orderRepository.save(expiredOrder)).willReturn(expiredOrder);

        scheduler.expireReservations();

        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(orderRepository).should().save(expiredOrder);

        ArgumentCaptor<OrderExpiredEvent> captor = ArgumentCaptor.forClass(OrderExpiredEvent.class);
        then(kafkaTemplate).should().send(eq("order-expired"), captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(captor.getValue().getProductId()).isEqualTo(productId);
        assertThat(captor.getValue().getQuantity()).isEqualTo(3);
    }

    @Test
    void expireReservations_whenNoExpiredOrders_shouldDoNothing() {
        given(orderRepository.findAllByStatusAndReservedUntilBefore(eq(OrderStatus.CREATED), any(Instant.class)))
                .willReturn(List.of());

        scheduler.expireReservations();

        then(orderRepository).should(never()).save(any());
        then(kafkaTemplate).shouldHaveNoInteractions();
    }
}
