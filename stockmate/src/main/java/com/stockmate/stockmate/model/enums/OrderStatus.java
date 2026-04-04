package com.stockmate.stockmate.model.enums;

/**
 * Lifecycle states of an Order.
 * Transitions: PENDING → CONFIRMED → SHIPPED → DELIVERED
 * At any point before DELIVERED: CANCELLED (by BUYER)
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}