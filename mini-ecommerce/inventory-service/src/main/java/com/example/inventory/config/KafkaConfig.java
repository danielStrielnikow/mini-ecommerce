package com.example.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String STOCK_DEPLETED_TOPIC = "stock-depleted";
    public static final String STOCK_RESTORED_TOPIC = "stock-restored";

    @Bean
    public NewTopic stockDepletedTopic() {
        return TopicBuilder.name(STOCK_DEPLETED_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic stockRestoredTopic() {
        return TopicBuilder.name(STOCK_RESTORED_TOPIC).partitions(1).replicas(1).build();
    }
}
