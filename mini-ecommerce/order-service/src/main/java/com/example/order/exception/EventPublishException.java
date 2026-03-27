package com.example.order.exception;

public class EventPublishException extends RuntimeException {

    public EventPublishException(String topic, Throwable cause) {
        super("Failed to publish event to topic '%s': %s".formatted(topic, cause.getMessage()), cause);
    }
}
