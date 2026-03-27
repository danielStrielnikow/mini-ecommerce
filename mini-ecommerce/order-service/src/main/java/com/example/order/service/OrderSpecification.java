package com.example.order.service;

import com.example.order.entity.Order;
import org.springframework.data.jpa.domain.Specification;

public class OrderSpecification {

    private OrderSpecification() {}

    public static Specification<Order> withFilter(OrderFilter filter) {
        Specification<Order> spec = Specification.where(null);

        if (filter.status() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), filter.status()));
        }

        if (filter.productId() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("productId"), filter.productId()));
        }

        return spec;
    }
}
