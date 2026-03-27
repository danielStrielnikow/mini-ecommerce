package com.example.product.service;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.response.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    Page<ProductSummaryResponse> findAll(ProductFilter filter, Pageable pageable);

    ProductResponse findById(UUID id);

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(UUID id, UpdateProductRequest request);

    void delete(UUID id);

    void hardDelete(UUID id);

    ProductResponse deactivate(UUID id);

    ProductResponse activate(UUID id);
}
