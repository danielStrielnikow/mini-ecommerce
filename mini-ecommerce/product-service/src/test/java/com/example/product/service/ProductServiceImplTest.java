package com.example.product.service;

import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.repository.ProductRepository;
import com.example.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        product = new Product();
        product.setId(productId);
        product.setName("Laptop Pro");
        product.setDescription("High-end laptop");
        product.setPrice(new BigDecimal("4999.99"));
        product.setCreatedAt(Instant.now());
    }

    @Test
    void findAll_shouldReturnListOfProductResponses() {
        given(productRepository.findAll()).willReturn(List.of(product));

        List<ProductResponse> result = productService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Laptop Pro");
        assertThat(result.get(0).price()).isEqualByComparingTo("4999.99");
        then(productRepository).should(times(1)).findAll();
    }

    @Test
    void findAll_whenNoProducts_shouldReturnEmptyList() {
        given(productRepository.findAll()).willReturn(List.of());

        List<ProductResponse> result = productService.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findById_whenProductExists_shouldReturnResponse() {
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        ProductResponse result = productService.findById(productId);

        assertThat(result.id()).isEqualTo(productId);
        assertThat(result.name()).isEqualTo("Laptop Pro");
        assertThat(result.description()).isEqualTo("High-end laptop");
    }

    @Test
    void findById_whenProductNotFound_shouldThrowProductNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(unknownId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}
