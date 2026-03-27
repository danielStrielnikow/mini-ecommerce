package com.example.product.service;

import com.example.product.entity.Product;
import com.example.product.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> withFilter(ProductFilter filter) {
        return Specification
                .where(nameLike(filter.name()))
                .and(priceGte(filter.minPrice()))
                .and(priceLte(filter.maxPrice()))
                .and(hasStatus(filter.status()));
    }

    // Zwraca null gdy brak wartości
    private static Specification<Product> nameLike(String name) {
        return (root, query, cb) ->
                name == null || name.isBlank() ? null
                        : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    private static Specification<Product> priceGte(BigDecimal min) {
        return (root, query, cb) ->
                min == null ? null : cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    private static Specification<Product> priceLte(BigDecimal max) {
        return (root, query, cb) ->
                max == null ? null : cb.lessThanOrEqualTo(root.get("price"), max);
    }

    private static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }
}
