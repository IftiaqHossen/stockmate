package com.stockmate.stockmate.repository;


import com.stockmate.stockmate.model.Order;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the orders table.
 *
 * Queries needed by the service layer:
 *
 *  OrderService (FR-ORD-01/04):
 *    - place order                              → save() [inherited]
 *    - buyer cancels order                      → findByIdAndBuyer() [ownership check]
 *    - buyer views own orders                   → findByBuyer()
 *
 *  OrderService (FR-ORD-05):
 *    - seller views orders for their products   → findByProductSellerId()
 *
 *  OrderService (FR-ORD-06):
 *    - admin views all orders                   → findAll() [inherited]
 *
 *  OrderService (FR-ORD-07):
 *    - update order status (SELLER/ADMIN)       → findById() [inherited] + save()
 *
 * JOIN FETCH is used throughout to avoid N+1 selects when mapping
 * Order → OrderResponse DTO (which needs buyer username, product name, seller).
 *
 * No business logic here — just query declarations.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // ── Buyer queries (FR-ORD-04) ─────────────────────────────

    /**
     * Buyer views their own order history, newest first.
     * JOIN FETCH product and its seller so the DTO can include product name
     * and seller name without additional round-trips.
     */
    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.product p
            JOIN FETCH p.seller
            WHERE o.buyer = :buyer
            ORDER BY o.orderedAt DESC
            """)
    List<Order> findByBuyer(@Param("buyer") User buyer);

    /**
     * Ownership check before a buyer cancels an order (FR-ORD-01 / FR-AUTH-09).
     * Returns empty if the order exists but belongs to a different buyer.
     * OrderServiceImpl throws AccessDeniedException on empty — not ResourceNotFoundException.
     */
    Optional<Order> findByIdAndBuyer(Long id, User buyer);

    // ── Seller queries (FR-ORD-05) ────────────────────────────

    /**
     * Seller views all orders placed against their products.
     * Traverses: Order → product → seller (via seller_id FK).
     * JOIN FETCH buyer so the DTO can show who placed the order.
     */
    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.buyer
            JOIN FETCH o.product p
            WHERE p.seller.id = :sellerId
            ORDER BY o.orderedAt DESC
            """)
    List<Order> findByProductSellerId(@Param("sellerId") Long sellerId);

    /**
     * Seller views orders filtered by status — useful for dashboard
     * (e.g. show only PENDING orders that need confirmation).
     */
    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.buyer
            JOIN FETCH o.product p
            WHERE p.seller.id = :sellerId
              AND o.status = :status
            ORDER BY o.orderedAt DESC
            """)
    List<Order> findByProductSellerIdAndStatus(
            @Param("sellerId") Long sellerId,
            @Param("status") OrderStatus status);

    // ── Admin queries (FR-ORD-06) ─────────────────────────────

    /**
     * Admin views all orders system-wide, newest first.
     * JOIN FETCH buyer and product (with seller) in one query to
     * avoid N+1 when building OrderResponse DTOs for every row.
     */
    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.buyer
            JOIN FETCH o.product p
            JOIN FETCH p.seller
            ORDER BY o.orderedAt DESC
            """)
    List<Order> findAllWithDetails();

    /**
     * Admin filters all orders by status.
     * Useful for admin dashboard views (e.g. all PENDING orders).
     */
    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.buyer
            JOIN FETCH o.product p
            JOIN FETCH p.seller
            WHERE o.status = :status
            ORDER BY o.orderedAt DESC
            """)
    List<Order> findAllByStatus(@Param("status") OrderStatus status);

    // ── Stock & stats helpers ─────────────────────────────────

    /**
     * Count orders for a product — used before a seller tries to delete
     * a product that already has order history (data integrity guard).
     */
    long countByProductId(Long productId);
}