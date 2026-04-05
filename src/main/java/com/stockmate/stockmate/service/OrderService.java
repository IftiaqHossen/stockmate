package com.stockmate.stockmate.service;


import com.stockmate.stockmate.dto.request.PlaceOrderRequest;
import com.stockmate.stockmate.dto.request.UpdateOrderStatusRequest;
import com.stockmate.stockmate.dto.response.OrderResponse;

import java.util.List;

/**
 * OrderService — all order-lifecycle business rules.
 *
 * Covers: place (BUYER), cancel (BUYER), view (BUYER / SELLER / ADMIN),
 * and status update (SELLER / ADMIN).
 *
 * Stock adjustment is this service's responsibility:
 *   • Place order   → decrement product.stockQuantity
 *   • Cancel order  → increment product.stockQuantity
 * These operations are transactional — partial failure rolls back.
 */
public interface OrderService {

    // ── BUYER operations ──────────────────────────────────────

    /**
     * Place a new order — BUYER only (FR-ORD-01/02/03).
     *
     * Inputs     : PlaceOrderRequest { productId, quantity }, buyerUsername
     * Validation : product must exist · requested qty ≤ available stock
     *              · @PreAuthorize BUYER
     * Repos      : ProductRepository (load + save stock) · UserRepository · OrderRepository.save()
     * Business   : validate stock → decrement stock → compute totalPrice → persist PENDING order
     * Output     : OrderResponse
     */
    OrderResponse placeOrder(PlaceOrderRequest request, String buyerUsername);

    /**
     * Cancel an order — BUYER only, own orders only (FR-ORD-01 / FR-AUTH-09).
     *
     * Inputs     : orderId (Long), buyerUsername
     * Validation : order must exist · order must belong to this buyer (ownership)
     *              · order must not be DELIVERED · @PreAuthorize BUYER
     * Repos      : OrderRepository + ProductRepository (stock increment)
     * Business   : set status → CANCELLED · increment product stock
     * Output     : updated OrderResponse
     */
    OrderResponse cancelOrder(Long orderId, String buyerUsername);

    /**
     * Buyer views their own order history (FR-ORD-04).
     *
     * Inputs     : buyerUsername
     * Validation : @PreAuthorize BUYER · user must exist
     * Repos      : UserRepository + OrderRepository.findByBuyer()
     * Business   : none — map to DTO
     * Output     : List<OrderResponse>
     */
    List<OrderResponse> getOrdersByBuyer(String buyerUsername);

    // ── SELLER operations ─────────────────────────────────────

    /**
     * Seller views orders placed against their products (FR-ORD-05).
     *
     * Inputs     : sellerUsername
     * Validation : @PreAuthorize SELLER · user must exist
     * Repos      : UserRepository + OrderRepository.findByProductSellerId()
     * Business   : none — map to DTO
     * Output     : List<OrderResponse>
     */
    List<OrderResponse> getOrdersBySeller(String sellerUsername);

    /**
     * Update order status — SELLER (own products' orders) or ADMIN (FR-ORD-07).
     *
     * Inputs     : orderId, UpdateOrderStatusRequest { newStatus }, currentUsername
     * Validation : order must exist · SELLER may only update orders for their own products
     *              · ADMIN may update any · CANCELLED/DELIVERED are terminal (no further change)
     *              · @PreAuthorize SELLER or ADMIN
     * Repos      : OrderRepository + UserRepository
     * Business   : ownership check for SELLER; terminal state guard
     * Output     : updated OrderResponse
     */
    OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request,
                                    String currentUsername);

    // ── ADMIN operations ──────────────────────────────────────

    /**
     * Admin views all orders system-wide (FR-ORD-06).
     *
     * Inputs     : none
     * Validation : @PreAuthorize ADMIN
     * Repos      : OrderRepository.findAllWithDetails()
     * Business   : none — map to DTO
     * Output     : List<OrderResponse>
     */
    List<OrderResponse> getAllOrders();
}