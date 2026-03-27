package com.example.e2e;

import com.example.inventory.InventoryServiceApplication;
import com.example.inventory.dto.response.InventoryResponse;
import com.example.inventory.entity.Inventory;
import com.example.inventory.repository.InventoryRepository;
import com.example.order.OrderServiceApplication;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.enums.OrderStatus;
import com.example.product.ProductServiceApplication;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test: starts all 3 services programmatically with shared
 * Testcontainers infrastructure (PostgreSQL × 3 DBs, Kafka, Redis).
 *
 * Flow:
 *   1.  Seed product + inventory via JPA
 *   2.  GET /api/products/{id} → 200, name + price correct
 *   3.  GET /api/inventory/check → true
 *   4.  POST /api/orders (qty=3, price=5999.99) → 201, totalPrice=17999.97, reservedUntil set
 *   5.  Async: wait for OrderCreatedEvent consumption → inventory 10→7
 *   6.  POST /api/orders (qty=999) → 409 insufficient stock
 *   7.  PATCH /api/orders/{id}/confirm → CONFIRMED
 *   8.  PATCH /api/orders/{id}/confirm again → CONFIRMED (idempotent)
 *   9.  Create second order (qty=2), wait stock 7→5, cancel, wait stock 5→7 (restored)
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullOrderFlowE2ETest {

    // ── Shared infrastructure ────────────────────────────────────────────────

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withUsername("test")
                    .withPassword("test")
                    .withDatabaseName("postgres");

    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @SuppressWarnings("resource")
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    // ── Fixed ports for each service ─────────────────────────────────────────

    static final int PRODUCT_PORT   = 18081;
    static final int ORDER_PORT     = 18082;
    static final int INVENTORY_PORT = 18083;

    // ── Spring contexts ──────────────────────────────────────────────────────

    static ConfigurableApplicationContext productCtx;
    static ConfigurableApplicationContext inventoryCtx;
    static ConfigurableApplicationContext orderCtx;

    // HttpComponentsClientHttpRequestFactory enables PATCH support
    static final RestTemplate http = new RestTemplate(new HttpComponentsClientHttpRequestFactory());

    // ── Test state shared across ordered steps ───────────────────────────────

    static UUID productId;
    static UUID savedOrderId;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @BeforeAll
    static void startAll() throws Exception {
        postgres.start();
        kafka.start();
        redis.start();

        createDatabases();

        String pgBase      = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432);
        String kafkaServer = kafka.getBootstrapServers();
        String redisHost   = redis.getHost();
        String redisPort   = redis.getMappedPort(6379).toString();

        productCtx = SpringApplication.run(ProductServiceApplication.class,
                "--server.port=" + PRODUCT_PORT,
                "--spring.datasource.url=" + pgBase + "/product_db",
                "--spring.datasource.username=test",
                "--spring.datasource.password=test",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.flyway.enabled=true",
                "--spring.flyway.locations=classpath:db/migration/product",
                "--spring.data.redis.host=" + redisHost,
                "--spring.data.redis.port=" + redisPort,
                "--spring.kafka.bootstrap-servers=" + kafkaServer
        );

        inventoryCtx = SpringApplication.run(InventoryServiceApplication.class,
                "--server.port=" + INVENTORY_PORT,
                "--spring.datasource.url=" + pgBase + "/inventory_db",
                "--spring.datasource.username=test",
                "--spring.datasource.password=test",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.flyway.enabled=true",
                "--spring.flyway.locations=classpath:db/migration/inventory",
                "--spring.kafka.bootstrap-servers=" + kafkaServer
        );

        orderCtx = SpringApplication.run(OrderServiceApplication.class,
                "--server.port=" + ORDER_PORT,
                "--spring.datasource.url=" + pgBase + "/order_db",
                "--spring.datasource.username=test",
                "--spring.datasource.password=test",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.flyway.enabled=true",
                "--spring.flyway.locations=classpath:db/migration/order",
                "--spring.kafka.bootstrap-servers=" + kafkaServer,
                "--app.inventory.base-url=http://localhost:" + INVENTORY_PORT,
                "--app.product.base-url=http://localhost:" + PRODUCT_PORT
        );

        // Wait for all Kafka consumer groups to fully subscribe before tests begin.
        // Consumers start asynchronously after Spring context refresh; in a containerised
        // environment the group rebalance can take up to ~15 s.
        Thread.sleep(20_000);
    }

    @AfterAll
    static void stopAll() {
        if (orderCtx     != null) orderCtx.close();
        if (inventoryCtx != null) inventoryCtx.close();
        if (productCtx   != null) productCtx.close();
    }

    // ── Steps ────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void step1_seedProductAndInventory() {
        // Insert directly via each service's own JPA context
        ProductRepository productRepo = productCtx.getBean(ProductRepository.class);
        Product product = new Product();
        product.setName("Gaming Laptop");
        product.setDescription("High-end gaming laptop");
        product.setPrice(new BigDecimal("5999.99"));
        productId = productRepo.save(product).getId();

        InventoryRepository invRepo = inventoryCtx.getBean(InventoryRepository.class);
        Inventory inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setQuantity(10);
        invRepo.save(inventory);

        assertThat(productId).isNotNull();
    }

    @Test
    @Order(2)
    void step2_getProductById_shouldReturn200WithCorrectData() {
        ResponseEntity<ProductResponse> response = http.getForEntity(
                "http://localhost:" + PRODUCT_PORT + "/api/products/{id}",
                ProductResponse.class, productId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Gaming Laptop");
        assertThat(response.getBody().price()).isEqualByComparingTo("5999.99");
    }

    @Test
    @Order(3)
    void step3_checkInventoryAvailability_shouldReturnTrue() {
        ResponseEntity<Boolean> response = http.getForEntity(
                "http://localhost:" + INVENTORY_PORT + "/api/inventory/check?productId={id}&quantity=2",
                Boolean.class, productId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    @Order(4)
    void step4_createOrder_shouldReturn201WithCorrectTotalPrice() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<OrderResponse> response = http.postForEntity(
                "http://localhost:" + ORDER_PORT + "/api/orders",
                new HttpEntity<>(new CreateOrderRequest(productId, 3), headers),
                OrderResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.getBody().productId()).isEqualTo(productId);
        assertThat(response.getBody().totalPrice()).isEqualByComparingTo("17999.97"); // 5999.99 × 3
        assertThat(response.getBody().reservedUntil()).isNotNull();

        savedOrderId = response.getBody().id();
    }

    @Test
    @Order(5)
    void step5_inventoryStock_shouldDecreaseAsyncViaKafka() {
        // Kafka is async — inventory-service consumes OrderCreatedEvent and decreases stock
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            ResponseEntity<InventoryResponse> inv = http.getForEntity(
                    "http://localhost:" + INVENTORY_PORT + "/api/inventory/{productId}",
                    InventoryResponse.class, productId
            );
            assertThat(inv.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(inv.getBody().quantity()).isEqualTo(7); // 10 - 3
        });
    }

    @Test
    @Order(6)
    void step6_createOrder_whenInsufficientStock_shouldReturn409() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThatThrownBy(() ->
                http.postForEntity(
                        "http://localhost:" + ORDER_PORT + "/api/orders",
                        new HttpEntity<>(new CreateOrderRequest(productId, 999), headers),
                        OrderResponse.class
                )
        ).isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    @Order(7)
    void step7_confirmOrder_shouldReturnConfirmed() {
        ResponseEntity<OrderResponse> response = http.exchange(
                "http://localhost:" + ORDER_PORT + "/api/orders/{id}/confirm",
                HttpMethod.PATCH, null, OrderResponse.class, savedOrderId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @Order(8)
    void step8_confirmOrder_whenAlreadyConfirmed_isIdempotent() {
        ResponseEntity<OrderResponse> response = http.exchange(
                "http://localhost:" + ORDER_PORT + "/api/orders/{id}/confirm",
                HttpMethod.PATCH, null, OrderResponse.class, savedOrderId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @Order(9)
    void step9_cancelOrder_shouldRestoreInventoryViaKafka() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // inventory is 7 after step5; order 2 units
        ResponseEntity<OrderResponse> created = http.postForEntity(
                "http://localhost:" + ORDER_PORT + "/api/orders",
                new HttpEntity<>(new CreateOrderRequest(productId, 2), headers),
                OrderResponse.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID secondOrderId = created.getBody().id();

        // Wait for stock to drop 7→5 (OrderCreatedEvent consumed)
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            InventoryResponse inv = http.getForEntity(
                    "http://localhost:" + INVENTORY_PORT + "/api/inventory/{productId}",
                    InventoryResponse.class, productId
            ).getBody();
            assertThat(inv.quantity()).isEqualTo(5); // 7 - 2
        });

        // Cancel the order → publishes OrderExpiredEvent → inventory restores
        ResponseEntity<OrderResponse> cancelled = http.exchange(
                "http://localhost:" + ORDER_PORT + "/api/orders/{id}/cancel",
                HttpMethod.PATCH, null, OrderResponse.class, secondOrderId
        );
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo(OrderStatus.CANCELLED);

        // Wait for stock to restore 5→7 (OrderExpiredEvent consumed)
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            InventoryResponse inv = http.getForEntity(
                    "http://localhost:" + INVENTORY_PORT + "/api/inventory/{productId}",
                    InventoryResponse.class, productId
            ).getBody();
            assertThat(inv.quantity()).isEqualTo(7); // 5 + 2 restored
        });
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static void createDatabases() throws Exception {
        try (var conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE product_db");
            stmt.execute("CREATE DATABASE order_db");
            stmt.execute("CREATE DATABASE inventory_db");
        }
    }
}
