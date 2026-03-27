package com.example.product.service;

import com.example.product.dto.response.ProductResponse;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    List<ProductResponse> findAll();

    ProductResponse findById(UUID id);
}
