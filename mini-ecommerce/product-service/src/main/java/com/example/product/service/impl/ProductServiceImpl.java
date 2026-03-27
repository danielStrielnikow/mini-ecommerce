package com.example.product.service.impl;

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
    private final KafkaTemplate<String, ProductDeletedEvent> kafkaTemplate;

    @Override
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
    @CacheEvict(value = "product", allEntries = true)
    public ProductResponse create(CreateProductRequest request) {
        Product product = productMapper.toEntity(request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        productMapper.updateEntity(request, product);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
    }

    @Override
    @Transactional
    @CacheEvict(value = "product", key = "#id")
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
    @CacheEvict(value = "product", key = "#id")
    public ProductResponse deactivate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setStatus(ProductStatus.INACTIVE);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public ProductResponse activate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setStatus(ProductStatus.ACTIVE);
        return productMapper.toResponse(productRepository.save(product));
    }
}
