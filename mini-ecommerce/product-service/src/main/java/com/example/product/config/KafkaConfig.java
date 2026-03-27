package com.example.product.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String PRODUCT_DELETED_TOPIC = "product-deleted";
    public static final String STOCK_DEPLETED_TOPIC  = "stock-depleted";

    @Bean
    public NewTopic productDeletedTopic() {
        return TopicBuilder.name(PRODUCT_DELETED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
