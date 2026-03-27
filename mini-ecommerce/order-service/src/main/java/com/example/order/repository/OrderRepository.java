package com.example.order.repository;

import com.example.order.entity.Order;
import com.example.order.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    List<Order> findAllByStatusAndReservedUntilBefore(OrderStatus status, Instant now);
}
