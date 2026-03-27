package com.example.inventory.service;

import com.example.inventory.entity.Inventory;
import org.springframework.data.jpa.domain.Specification;

public class InventorySpecification {

    private InventorySpecification() {}

    public static Specification<Inventory> withFilter(InventoryFilter filter) {
        return Specification
                .where(isAvailable(filter.available()))
                .and(quantityGte(filter.minQuantity()))
                .and(quantityLte(filter.maxQuantity()));
    }

    private static Specification<Inventory> isAvailable(Boolean available) {
        if (available == null) return null;
        return (root, query, cb) -> available
                ? cb.greaterThan(root.get("quantity"), 0)
                : cb.equal(root.get("quantity"), 0);
    }

    private static Specification<Inventory> quantityGte(Integer min) {
        if (min == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("quantity"), min);
    }

    private static Specification<Inventory> quantityLte(Integer max) {
        if (max == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("quantity"), max);
    }
}
