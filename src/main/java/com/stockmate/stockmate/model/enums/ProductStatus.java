package com.stockmate.stockmate.model.enums;

/**
 * Lifecycle status of a Product listing.
 * Combined with stock_quantity to compute the visible stock status
 * in the service layer (never stored as a column):
 *
 *   stock_quantity > 0              → "In Stock"    (either status)
 *   stock_quantity = 0 AND ACTIVE   → "Pre Order"
 *   stock_quantity = 0 AND DISCONTINUED → "Out of Stock"
 */
public enum ProductStatus {
    ACTIVE,
    DISCONTINUED
}