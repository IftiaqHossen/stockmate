package com.stockmate.stockmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** CreateCategoryRequest — used for both create and update (same fields). */
public record CreateCategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String name,

        String description   // nullable
) {}