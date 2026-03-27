package com.example.product.dto.request;

import com.example.product.entity.ProductStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateProductRequest(

        @NotBlank(message = "Name is required")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer and 2 decimal digits")
        BigDecimal price,

        @NotNull(message = "Status is required")
        ProductStatus status
) {}
