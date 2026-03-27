package com.example.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ORDER_CREATED_TOPIC  = "order-created";
    public static final String ORDER_EXPIRED_TOPIC  = "order-expired";

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(ORDER_CREATED_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic orderExpiredTopic() {
        return TopicBuilder.name(ORDER_EXPIRED_TOPIC).partitions(1).replicas(1).build();
    }
}
