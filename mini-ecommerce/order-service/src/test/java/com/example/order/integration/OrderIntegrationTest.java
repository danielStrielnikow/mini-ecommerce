package com.example.order.integration;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.enums.OrderStatus;
import com.example.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.order.client.InventoryClient;
import com.example.order.client.ProductClient;
import com.example.order.dto.response.ProductPriceResponse;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        // Kafka not needed — external clients are mocked via @MockBean
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.kafka.producer.retries", () -> "0");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private OrderRepository orderRepository;

    @MockBean private InventoryClient inventoryClient;
    @MockBean private ProductClient productClient;

    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        given(productClient.getProduct(any()))
                .willReturn(new ProductPriceResponse(productId, "Laptop", new BigDecimal("999.99"), "ACTIVE"));
        given(inventoryClient.checkAvailability(any(), anyInt())).willReturn(true);
    }

    // ── createOrder ──────────────────────────────────────────────────────────

    @Test
    void createOrder_whenValid_shouldReturn201WithCorrectTotalPrice() {
        CreateOrderRequest request = new CreateOrderRequest(productId, 3);

        ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
                "/api/orders", request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.getBody().totalPrice()).isEqualByComparingTo("2999.97");
        assertThat(response.getBody().reservedUntil()).isNotNull();
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void createOrder_whenStockUnavailable_shouldReturn409() {
        given(inventoryClient.checkAvailability(any(), anyInt())).willReturn(false);
        CreateOrderRequest request = new CreateOrderRequest(productId, 99);

        ResponseEntity<Void> response = restTemplate.postForEntity("/api/orders", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void createOrder_whenQuantityZero_shouldReturn400() {
        CreateOrderRequest request = new CreateOrderRequest(productId, 0);

        ResponseEntity<Void> response = restTemplate.postForEntity("/api/orders", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_whenExists_shouldReturn200() {
        OrderResponse created = restTemplate.postForEntity("/api/orders",
                new CreateOrderRequest(productId, 1), OrderResponse.class).getBody();

        ResponseEntity<OrderResponse> response = restTemplate.getForEntity(
                "/api/orders/{id}", OrderResponse.class, created.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(created.id());
    }

    @Test
    void getById_whenNotFound_shouldReturn404() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "/api/orders/{id}", Void.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── cancel ───────────────────────────────────────────────────────────────

    @Test
    void cancel_whenCreated_shouldReturn200WithCancelledStatus() {
        OrderResponse created = restTemplate.postForEntity("/api/orders",
                new CreateOrderRequest(productId, 2), OrderResponse.class).getBody();

        ResponseEntity<OrderResponse> response = restTemplate.exchange(
                "/api/orders/{id}/cancel", HttpMethod.PATCH, null,
                OrderResponse.class, created.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ── confirm ──────────────────────────────────────────────────────────────

    @Test
    void confirm_whenCreated_shouldReturn200WithConfirmedStatus() {
        OrderResponse created = restTemplate.postForEntity("/api/orders",
                new CreateOrderRequest(productId, 1), OrderResponse.class).getBody();

        ResponseEntity<OrderResponse> response = restTemplate.exchange(
                "/api/orders/{id}/confirm", HttpMethod.PATCH, null,
                OrderResponse.class, created.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_whenAlreadyCancelled_shouldReturn409() {
        OrderResponse created = restTemplate.postForEntity("/api/orders",
                new CreateOrderRequest(productId, 1), OrderResponse.class).getBody();

        restTemplate.exchange("/api/orders/{id}/cancel", HttpMethod.PATCH, null,
                Void.class, created.id());

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/orders/{id}/confirm", HttpMethod.PATCH, null,
                Void.class, created.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
