package com.stockmate.stockmate.dto.request;


import com.stockmate.stockmate.model.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** UpdateProductRequest — SELLER/ADMIN payload for PUT /products/{id}. */
public record UpdateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 200)
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal price,

        @Min(value = 0, message = "Stock quantity must be >= 0")
        int stockQuantity,

        @NotNull(message = "Status is required")
        ProductStatus status,

        Long categoryId   // nullable — null means keep current category
) {}