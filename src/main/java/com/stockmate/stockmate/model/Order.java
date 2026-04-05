package com.stockmate.stockmate.model;

import com.stockmate.stockmate.model.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A purchase order placed by a BUYER for a specific Product.
 *
 * Key design decisions:
 * - total_price is stored (snapshot at time of order) — product price may change later
 * - status transitions: PENDING → CONFIRMED → SHIPPED → DELIVERED | CANCELLED
 * - buyer and product are M:1 LAZY — loaded only when explicitly needed
 * - stock adjustment (decrement/increment) is handled in OrderService,
 *   NOT here — this entity just records the order fact
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * M:1 → users (the BUYER who placed this order).
     * nullable = false — every order must have a buyer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    /**
     * M:1 → products.
     * nullable = false — an order always refers to a product.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Snapshot of (quantity × product.price) at the moment of ordering.
     * Stored so historical order totals remain correct even if the
     * product price changes later.
     */
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private LocalDateTime orderedAt;

    // ── Lifecycle ─────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.orderedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────

    protected Order() {
        // JPA required
    }

    public Order(User buyer, Product product, int quantity, BigDecimal totalPrice) {
        this.buyer      = buyer;
        this.product    = product;
        this.quantity   = quantity;
        this.totalPrice = totalPrice;
        this.status     = OrderStatus.PENDING;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long getId()                  { return id; }
    public User getBuyer()               { return buyer; }
    public Product getProduct()          { return product; }
    public int getQuantity()             { return quantity; }
    public BigDecimal getTotalPrice()    { return totalPrice; }
    public OrderStatus getStatus()       { return status; }
    public LocalDateTime getOrderedAt()  { return orderedAt; }

    // ── Setters ───────────────────────────────────────────────

    public void setBuyer(User buyer)         { this.buyer = buyer; }
    public void setProduct(Product product)  { this.product = product; }
    public void setQuantity(int quantity)    { this.quantity = quantity; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    /**
     * Status transitions are managed by OrderService.
     * Direct setter kept here for service layer use only.
     */
    public void setStatus(OrderStatus status) { this.status = status; }
}