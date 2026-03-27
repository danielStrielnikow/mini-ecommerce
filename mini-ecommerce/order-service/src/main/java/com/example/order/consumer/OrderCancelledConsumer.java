package com.example.order.consumer;

import com.example.events.OrderCancelledEvent;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = "order-cancelled", groupId = "order-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Order cancelled event received: orderId={}, reason={}",
                event.getOrderId(), event.getReason());
        try {
            orderService.cancelOrderByEvent(event.getOrderId());
        } catch (OrderNotFoundException e) {
            log.warn("Order not found for cancellation: orderId={}", event.getOrderId());
        }
    }
}
