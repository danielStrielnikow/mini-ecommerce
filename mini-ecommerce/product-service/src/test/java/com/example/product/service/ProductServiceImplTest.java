package com.example.product.service;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import com.example.product.entity.ProductStatus;
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
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(Instant.now());

        productResponse = new ProductResponse(productId, "Laptop Pro", "High-end laptop",
                new BigDecimal("4999.99"), ProductStatus.ACTIVE, Instant.now());
    }

    // ── findAll ──────────────────────────────────────────────────────────────

    @Test
    void findAll_shouldReturnPageOfProductResponses() {
        given(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(product)));
        given(productMapper.toResponse(product)).willReturn(productResponse);

        Page<ProductResponse> result = productService.findAll(new ProductFilter(null, null, null, null), Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Laptop Pro");
    }

    @Test
    void findAll_whenNoProducts_shouldReturnEmptyPage() {
        given(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(Page.empty());

        Page<ProductResponse> result = productService.findAll(new ProductFilter(null, null, null, null), Pageable.unpaged());

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void findAll_withFilter_shouldPassSpecificationToRepository() {
        ProductFilter filter = new ProductFilter("laptop", new BigDecimal("100"), new BigDecimal("9999"), ProductStatus.ACTIVE);
        given(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(product)));
        given(productMapper.toResponse(product)).willReturn(productResponse);

        Page<ProductResponse> result = productService.findAll(filter, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        then(productRepository).should().findAll(any(Specification.class), any(Pageable.class));
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_whenProductExists_shouldReturnResponse() {
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productMapper.toResponse(product)).willReturn(productResponse);

        ProductResponse result = productService.findById(productId);

        assertThat(result.id()).isEqualTo(productId);
        assertThat(result.name()).isEqualTo("Laptop Pro");
        assertThat(result.status()).isEqualTo(ProductStatus.ACTIVE);
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
    void create_shouldSaveProductWithActiveStatusAndReturnResponse() {
        CreateProductRequest request = new CreateProductRequest(
                "Laptop Pro", "High-end laptop", new BigDecimal("4999.99"));
        given(productMapper.toEntity(request)).willReturn(product);
        given(productRepository.save(product)).willReturn(product);
        given(productMapper.toResponse(product)).willReturn(productResponse);

        ProductResponse result = productService.create(request);

        assertThat(result.status()).isEqualTo(ProductStatus.ACTIVE);
        then(productRepository).should().save(product);
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_whenProductExists_shouldUpdateAndReturnResponse() {
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Laptop", "Updated desc", new BigDecimal("5999.99"), ProductStatus.INACTIVE);
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
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated", null, new BigDecimal("100"), ProductStatus.ACTIVE);
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(unknownId, request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ── delete (soft) ────────────────────────────────────────────────────────

    @Test
    void delete_whenProductExists_shouldSoftDeleteIt() {
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        productService.delete(productId);

        assertThat(product.getDeletedAt()).isNotNull();
        then(productRepository).should().save(product);
        then(productRepository).should(never()).deleteById(any());
    }

    @Test
    void delete_whenNotFound_shouldThrowProductNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(unknownId))
                .isInstanceOf(ProductNotFoundException.class);
        then(productRepository).should(never()).save(any());
    }

    // ── hardDelete ───────────────────────────────────────────────────────────

    @Test
    void hardDelete_whenProductExists_shouldDeletePermanently() {
        given(productRepository.existsById(productId)).willReturn(true);

        productService.hardDelete(productId);

        then(productRepository).should().deleteById(productId);
        then(productRepository).should(never()).save(any());
    }

    @Test
    void hardDelete_whenNotFound_shouldThrowProductNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        given(productRepository.existsById(unknownId)).willReturn(false);

        assertThatThrownBy(() -> productService.hardDelete(unknownId))
                .isInstanceOf(ProductNotFoundException.class);
        then(productRepository).should(never()).deleteById(any());
    }
}
