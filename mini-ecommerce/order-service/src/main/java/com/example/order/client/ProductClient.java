package com.example.order.client;

import com.example.order.dto.response.ProductPriceResponse;
import com.example.order.exception.ProductNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductClient {

    private final RestClient restClient;

    @Value("${app.product.base-url}")
    private String productBaseUrl;

    @CircuitBreaker(name = "product", fallbackMethod = "getProductFallback")
    public ProductPriceResponse getProduct(UUID productId) {
        try {
            return restClient.get()
                    .uri(productBaseUrl + "/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductPriceResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId);
        }
    }

    private ProductPriceResponse getProductFallback(UUID productId, Throwable ex) {
        if (ex instanceof ProductNotFoundException) {
            throw (ProductNotFoundException) ex;
        }
        log.warn("Product service unavailable for productId={}. Cause: {}", productId, ex.getMessage());
        throw new ProductNotFoundException(productId);
    }
}
