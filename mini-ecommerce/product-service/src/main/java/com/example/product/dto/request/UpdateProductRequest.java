package com.example.product.dto.request;

import com.example.product.entity.ProductStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateProductRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Pattern(regexp = ".*\\p{L}.*", message = "Name must contain at least one letter")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer and 2 decimal digits")
        BigDecimal price,

        @NotNull(message = "Status is required")
        ProductStatus status
) {}
