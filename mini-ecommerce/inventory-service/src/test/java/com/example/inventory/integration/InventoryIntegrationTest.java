package com.example.inventory.integration;

import com.example.events.OrderCreatedEvent;
import com.example.inventory.dto.request.CreateInventoryRequest;
import com.example.inventory.dto.request.RestockRequest;
import com.example.inventory.dto.response.InventoryResponse;
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

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

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

    // ── check ────────────────────────────────────────────────────────────────

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

    // ── getByProductId ───────────────────────────────────────────────────────

    @Test
    void getByProductId_whenExists_shouldReturn200() {
        ResponseEntity<InventoryResponse> response = restTemplate.getForEntity(
                "/api/inventory/{productId}", InventoryResponse.class, productId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().productId()).isEqualTo(productId);
        assertThat(response.getBody().quantity()).isEqualTo(10);
        assertThat(response.getBody().available()).isTrue();
    }

    @Test
    void getByProductId_whenNotFound_shouldReturn404() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "/api/inventory/{productId}", Void.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_shouldReturn201AndPersist() {
        UUID newProductId = UUID.randomUUID();
        CreateInventoryRequest request = new CreateInventoryRequest(newProductId, 50);

        ResponseEntity<InventoryResponse> response = restTemplate.postForEntity(
                "/api/inventory", request, InventoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().productId()).isEqualTo(newProductId);
        assertThat(response.getBody().quantity()).isEqualTo(50);
        assertThat(inventoryRepository.existsByProductId(newProductId)).isTrue();
    }

    @Test
    void create_whenDuplicate_shouldReturn409() {
        CreateInventoryRequest request = new CreateInventoryRequest(productId, 10);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/inventory", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── restock ──────────────────────────────────────────────────────────────

    @Test
    void restock_shouldIncreaseQuantityAndReturn200() {
        RestockRequest request = new RestockRequest(20);

        restTemplate.patchForObject("/api/inventory/{productId}/restock", request,
                InventoryResponse.class, productId);

        Inventory updated = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(30);
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_shouldRemoveRecordAndReturn204() {
        restTemplate.delete("/api/inventory/{productId}", productId);

        assertThat(inventoryRepository.existsByProductId(productId)).isFalse();
    }

    @Test
    void delete_whenNotFound_shouldReturn404() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/inventory/{productId}",
                org.springframework.http.HttpMethod.DELETE,
                null,
                Void.class,
                UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Kafka: OrderCreatedEvent ──────────────────────────────────────────────

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

    @Test
    void whenOrderCreatedEventWithInsufficientStock_stockShouldNotChange() {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(UUID.randomUUID())
                .productId(productId)
                .quantity(99)
                .totalPrice(BigDecimal.TEN)
                .createdAt(Instant.now())
                .build();

        kafkaTemplate.send("order-created", event);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Inventory unchanged = inventoryRepository.findByProductId(productId).orElseThrow();
            assertThat(unchanged.getQuantity()).isEqualTo(10);
        });
    }
}
