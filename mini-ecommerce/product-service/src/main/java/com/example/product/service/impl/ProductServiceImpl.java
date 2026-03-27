package com.example.product.service.impl;

import com.example.events.ProductCreatedEvent;
import com.example.events.ProductDeletedEvent;
import com.example.product.config.KafkaConfig;
import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import com.example.product.entity.ProductStatus;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.mapper.ProductMapper;
import com.example.product.repository.ProductRepository;
import com.example.product.service.ProductFilter;
import com.example.product.service.ProductService;
import com.example.product.service.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Cacheable(value = "products-all",
               key = "#pageable.pageNumber + '-' + #pageable.pageSize",
               condition = "#filter.name == null && #filter.minPrice == null && #filter.maxPrice == null && #filter.status == null")
    public Page<ProductResponse> findAll(ProductFilter filter, Pageable pageable) {
        return productRepository
                .findAll(ProductSpecification.withFilter(filter), pageable)
                .map(productMapper::toResponse);
    }

    @Override
    @Cacheable(value = "product", key = "#id")
    public ProductResponse findById(UUID id) {
        return productRepository.findById(id)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product", allEntries = true),
            @CacheEvict(value = "products-all", allEntries = true)
    })
    public ProductResponse create(CreateProductRequest request) {
        Product product = productMapper.toEntity(request);
        Product saved = productRepository.save(product);
        kafkaTemplate.send(KafkaConfig.PRODUCT_CREATED_TOPIC,
                ProductCreatedEvent.builder()
                        .productId(saved.getId())
                        .occurredAt(Instant.now())
                        .build());
        return productMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products-all", allEntries = true)
    })
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        productMapper.updateEntity(request, product);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products-all", allEntries = true)
    })
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products-all", allEntries = true)
    })
    public void hardDelete(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        kafkaTemplate.send(KafkaConfig.PRODUCT_DELETED_TOPIC,
                ProductDeletedEvent.builder()
                        .productId(id)
                        .deletedAt(Instant.now())
                        .build());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products-all", allEntries = true)
    })
    public ProductResponse deactivate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setStatus(ProductStatus.INACTIVE);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "products-all", allEntries = true)
    })
    public ProductResponse activate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setStatus(ProductStatus.ACTIVE);
        return productMapper.toResponse(productRepository.save(product));
    }
}
