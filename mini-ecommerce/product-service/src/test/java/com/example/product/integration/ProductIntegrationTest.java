package com.example.product.integration;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.ProductStatus;
import com.example.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        // Use simple in-memory cache — RedisCacheManager fails to serialize Java records
        registry.add("spring.cache.type", () -> "simple");
        // Kafka not needed — KafkaTemplate is mocked, listeners disabled
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.kafka.producer.retries", () -> "0");
        registry.add("spring.kafka.admin.fail-fast", () -> "false");
        registry.add("spring.kafka.admin.operation-timeout", () -> "2");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ProductRepository productRepository;
    @MockBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean private KafkaAdmin kafkaAdmin;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_whenValid_shouldReturn201AndPersist() {
        CreateProductRequest request = new CreateProductRequest(
                "Laptop Pro", "High-end laptop", new BigDecimal("4999.99"));

        ResponseEntity<ProductResponse> response = restTemplate.postForEntity(
                "/api/products", request, ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Laptop Pro");
        assertThat(response.getBody().price()).isEqualByComparingTo("4999.99");
        assertThat(response.getBody().status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    void create_whenNameBlank_shouldReturn400() {
        CreateProductRequest request = new CreateProductRequest("", "desc", new BigDecimal("9.99"));

        ResponseEntity<Void> response = restTemplate.postForEntity("/api/products", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    @Test
    void findById_whenNotFound_shouldReturn404() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "/api/products/{id}", Void.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_whenExists_shouldReturn200WithUpdatedData() {
        ProductResponse created = restTemplate.postForEntity("/api/products",
                new CreateProductRequest("Old Name", null, new BigDecimal("10.00")),
                ProductResponse.class).getBody();

        UpdateProductRequest update = new UpdateProductRequest(
                "New Name", "desc", new BigDecimal("20.00"), ProductStatus.ACTIVE);
        ResponseEntity<ProductResponse> response = restTemplate.exchange(
                "/api/products/{id}", HttpMethod.PUT, new HttpEntity<>(update),
                ProductResponse.class, created.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("New Name");
        assertThat(response.getBody().price()).isEqualByComparingTo("20.00");
    }

    // ── soft delete ──────────────────────────────────────────────────────────

    @Test
    void delete_whenExists_shouldReturn204AndHideProduct() {
        ProductResponse created = restTemplate.postForEntity("/api/products",
                new CreateProductRequest("ToDelete", null, new BigDecimal("5.00")),
                ProductResponse.class).getBody();

        restTemplate.delete("/api/products/{id}", created.id());

        ResponseEntity<Void> getResponse = restTemplate.getForEntity(
                "/api/products/{id}", Void.class, created.id());
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── deactivate / activate ─────────────────────────────────────────────────

    @Test
    void deactivate_thenActivate_shouldCycleStatus() {
        ProductResponse created = restTemplate.postForEntity("/api/products",
                new CreateProductRequest("Keyboard", null, new BigDecimal("199.99")),
                ProductResponse.class).getBody();

        ResponseEntity<ProductResponse> deactivated = restTemplate.exchange(
                "/api/products/{id}/deactivate", HttpMethod.PATCH, null,
                ProductResponse.class, created.id());
        assertThat(deactivated.getBody().status()).isEqualTo(ProductStatus.INACTIVE);

        ResponseEntity<ProductResponse> activated = restTemplate.exchange(
                "/api/products/{id}/activate", HttpMethod.PATCH, null,
                ProductResponse.class, created.id());
        assertThat(activated.getBody().status()).isEqualTo(ProductStatus.ACTIVE);
    }
}
