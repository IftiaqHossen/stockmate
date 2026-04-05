package com.stockmate.stockmate.dto.request;

import com.stockmate.stockmate.model.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** UpdateOrderStatusRequest — SELLER/ADMIN payload for PUT /orders/{id}/status. */
public record UpdateOrderStatusRequest(

        @NotNull(message = "New status is required")
        OrderStatus newStatus
) {}