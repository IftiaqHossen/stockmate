package com.stockmate.stockmate.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * InsufficientStockException — thrown when a BUYER attempts to order more
 * units than are currently available in stock.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  WHERE IS IT THROWN?                                             │
 * │                                                                  │
 * │  OrderServiceImpl.placeOrder()                                   │
 * │    Condition: product.getStockQuantity() < request.quantity()    │
 * │    This is business rule FR-ORD-02:                              │
 * │    "System validates that requested quantity does not exceed      │
 * │     available stock."                                            │
 * │                                                                  │
 * │  Test coverage: OrderServiceTest.placeOrder_insufficientStock()  │
 * │                                                                  │
 * │  WHERE IS IT CAUGHT?                                             │
 * │    GlobalExceptionHandler.handleInsufficientStock()              │
 * │    → returns HTTP 400 Bad Request                                │
 * │    → message shown to the BUYER on the order form                │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * HTTP 400 (not 409 Conflict) — because this is a validation failure
 * on the client's input (requested qty > available qty), not a server
 * state conflict.
 *
 * Extends RuntimeException — no checked-exception boilerplate in callers.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientStockException extends RuntimeException {

    /**
     * @param message human-readable description shown to the user, e.g.
     *                "Insufficient stock for product 'Laptop X'.
     *                 Available: 3, requested: 10"
     */
    public InsufficientStockException(String message) {
        super(message);
    }

    /**
     * Constructor with cause — for wrapping lower-level errors while
     * preserving the original stack trace.
     */
    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}