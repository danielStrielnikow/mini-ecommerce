package com.example.inventory.integration;

import com.example.events.OrderCreatedEvent;
import com.example.inventory.entity.Inventory;
import com.example.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InventoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventory_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    private UUID productId;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        productId = UUID.randomUUID();

        Inventory inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setQuantity(10);
        inventory.setVersion(0L);
        inventoryRepository.save(inventory);
    }

    @Test
    void check_whenAvailable_shouldReturnTrue() {
        ResponseEntity<Boolean> response = restTemplate.getForEntity(
                "/api/inventory/check?productId={id}&quantity=5", Boolean.class, productId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void check_whenNotEnoughStock_shouldReturnFalse() {
        ResponseEntity<Boolean> response = restTemplate.getForEntity(
                "/api/inventory/check?productId={id}&quantity=99", Boolean.class, productId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();
    }

    @Test
    void whenOrderCreatedEventConsumed_shouldDecreaseStock() {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(UUID.randomUUID())
                .productId(productId)
                .quantity(3)
                .totalPrice(BigDecimal.TEN)
                .createdAt(Instant.now())
                .build();

        kafkaTemplate.send("order-created", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Inventory updated = inventoryRepository.findByProductId(productId).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(7);
        });
    }
}
