package com.stockmate.stockmate.dto.response;


import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ProductResponse — includes computed stockStatus (never stored in DB).
 * Returned by ProductService and exposed through ProductController.
 */
public record ProductResponse(
        Long          id,
        String        name,
        String        description,
        BigDecimal    price,
        int           stockQuantity,
        String        stockStatus,    // computed: "In Stock" | "Pre Order" | "Out of Stock"
        String        status,         // stored: "ACTIVE" | "DISCONTINUED"
        Long          categoryId,
        String        categoryName,
        Long          sellerId,
        String        sellerUsername,
        LocalDateTime createdAt
) {}