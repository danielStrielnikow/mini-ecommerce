package com.example.product.service;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.mapper.ProductMapper;
import com.example.product.repository.ProductRepository;
import com.example.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper productMapper;

    @InjectMocks private ProductServiceImpl productService;

    private Product product;
    private ProductResponse productResponse;
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

        productResponse = new ProductResponse(productId, "Laptop Pro", "High-end laptop",
                new BigDecimal("4999.99"), Instant.now());
    }

    // ── findAll ──────────────────────────────────────────────────────────────

    @Test
    void findAll_shouldReturnPageOfProductResponses() {
        given(productRepository.findAll(any(Pageable.class))).willReturn(new PageImpl<>(List.of(product)));
        given(productMapper.toResponse(product)).willReturn(productResponse);

        Page<ProductResponse> result = productService.findAll(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Laptop Pro");
        assertThat(result.getContent().get(0).price()).isEqualByComparingTo("4999.99");
    }

    @Test
    void findAll_whenNoProducts_shouldReturnEmptyPage() {
        given(productRepository.findAll(any(Pageable.class))).willReturn(Page.empty());

        Page<ProductResponse> result = productService.findAll(Pageable.unpaged());

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_whenProductExists_shouldReturnResponse() {
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productMapper.toResponse(product)).willReturn(productResponse);

        ProductResponse result = productService.findById(productId);

        assertThat(result.id()).isEqualTo(productId);
        assertThat(result.name()).isEqualTo("Laptop Pro");
        assertThat(result.description()).isEqualTo("High-end laptop");
    }

    @Test
    void findById_whenNotFound_shouldThrowProductNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(unknownId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_shouldSaveProductAndReturnResponse() {
        CreateProductRequest request = new CreateProductRequest(
                "Laptop Pro", "High-end laptop", new BigDecimal("4999.99"));
        given(productMapper.toEntity(request)).willReturn(product);
        given(productRepository.save(product)).willReturn(product);
        given(productMapper.toResponse(product)).willReturn(productResponse);

        ProductResponse result = productService.create(request);

        assertThat(result.name()).isEqualTo("Laptop Pro");
        then(productRepository).should().save(product);
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_whenProductExists_shouldUpdateAndReturnResponse() {
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Laptop", "Updated desc", new BigDecimal("5999.99"));
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productRepository.save(product)).willReturn(product);
        given(productMapper.toResponse(product)).willReturn(productResponse);

        ProductResponse result = productService.update(productId, request);

        assertThat(result).isNotNull();
        then(productMapper).should().updateEntity(request, product);
        then(productRepository).should().save(product);
    }

    @Test
    void update_whenNotFound_shouldThrowProductNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest("Updated", null, new BigDecimal("100"));
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(unknownId, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_whenProductExists_shouldDeleteIt() {
        given(productRepository.existsById(productId)).willReturn(true);

        productService.delete(productId);

        then(productRepository).should().deleteById(productId);
    }

    @Test
    void delete_whenNotFound_shouldThrowProductNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        given(productRepository.existsById(unknownId)).willReturn(false);

        assertThatThrownBy(() -> productService.delete(unknownId))
                .isInstanceOf(ProductNotFoundException.class);
        then(productRepository).should(never()).deleteById(any());
    }
}
