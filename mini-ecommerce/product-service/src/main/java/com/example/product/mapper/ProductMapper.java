package com.example.product.mapper;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.response.ProductSummaryResponse;
import com.example.product.entity.Product;
import com.example.product.entity.ProductStatus;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    ProductSummaryResponse toSummary(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "status", expression = "java(ProductStatus.ACTIVE)")
    Product toEntity(CreateProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntity(UpdateProductRequest request, @MappingTarget Product product);
}
