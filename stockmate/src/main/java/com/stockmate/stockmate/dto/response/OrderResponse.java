package com.stockmate.stockmate.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderResponse — returned by OrderService.
 * Contains denormalised buyer/product/seller info for display convenience.
 */
public record OrderResponse(
        Long          id,
        Long          buyerId,
        String        buyerUsername,
        Long          productId,
        String        productName,
        String        sellerUsername,
        int           quantity,
        BigDecimal    totalPrice,
        String        status,         // e.g. "PENDING"
        LocalDateTime orderedAt
) {}
