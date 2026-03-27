package com.example.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderExpiredEvent {
    private UUID orderId;
    private UUID productId;
    private Integer quantity;
    private Instant occurredAt;
}
