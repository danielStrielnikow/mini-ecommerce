package com.example.order.scheduler;

import com.example.events.OrderExpiredEvent;
import com.example.order.config.KafkaConfig;
import com.example.order.entity.Order;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireReservations() {
        List<Order> expired = orderRepository.findAllByStatusAndReservedUntilBefore(
                OrderStatus.CREATED, Instant.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("Found {} expired reservations to cancel", expired.size());

        for (Order order : expired) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            OrderExpiredEvent event = OrderExpiredEvent.builder()
                    .orderId(order.getId())
                    .productId(order.getProductId())
                    .quantity(order.getQuantity())
                    .occurredAt(Instant.now())
                    .build();

            kafkaTemplate.send(KafkaConfig.ORDER_EXPIRED_TOPIC, event);
            log.info("Reservation expired — order cancelled and event published: orderId={}", order.getId());
        }
    }
}
