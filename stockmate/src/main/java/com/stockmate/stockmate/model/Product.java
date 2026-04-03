package com.stockmate.stockmate.model;


import com.stockmate.stockmate.model.enums.ProductStatus;
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
 * A product listing created by a SELLER.
 *
 * Key design decisions:
 * - status (ACTIVE/DISCONTINUED) is stored — drives computed stock display
 * - stock_status is NOT stored — computed in ProductService from
 *   (stock_quantity + status). See CLAUDE.md "Stock Status (computed, never stored)"
 * - seller and category are M:1 — loaded LAZY to avoid N+1 on catalogue pages
 * - price uses BigDecimal (not double) to avoid floating-point precision errors
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;   // nullable

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity = 0;

    /**
     * Stored as a VARCHAR using the enum name (ACTIVE / DISCONTINUED).
     * Do NOT add a stock_status column — that is a computed value.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    /**
     * M:1 → categories.
     * Every product must belong to a category — nullable = false.
     * LAZY — don't load the category just to list product IDs.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * M:1 → users (the SELLER who created this product).
     * nullable = false — every product must have an owner.
     * LAZY — don't load full User object when listing products.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Lifecycle ─────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────

    public Product() {
        // JPA required
    }

    public Product(String name, String description, BigDecimal price,
                   int stockQuantity, Category category, User seller) {
        this.name          = name;
        this.description   = description;
        this.price         = price;
        this.stockQuantity = stockQuantity;
        this.category      = category;
        this.seller        = seller;
        this.status        = ProductStatus.ACTIVE;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long getId()                  { return id; }
    public String getName()              { return name; }
    public String getDescription()       { return description; }
    public BigDecimal getPrice()         { return price; }
    public int getStockQuantity()        { return stockQuantity; }
    public ProductStatus getStatus()     { return status; }
    public Category getCategory()        { return category; }
    public User getSeller()              { return seller; }
    public LocalDateTime getCreatedAt()  { return createdAt; }

    // ── Setters ───────────────────────────────────────────────

    public void setName(String name)                   { this.name = name; }
    public void setDescription(String description)     { this.description = description; }
    public void setPrice(BigDecimal price)             { this.price = price; }
    public void setStockQuantity(int stockQuantity)    { this.stockQuantity = stockQuantity; }
    public void setStatus(ProductStatus status)        { this.status = status; }
    public void setCategory(Category category)         { this.category = category; }
    public void setSeller(User seller)                 { this.seller = seller; }

    // ── Stock helpers (used by OrderServiceImpl) ──────────────

    public void decrementStock(int quantity) {
        this.stockQuantity -= quantity;
    }

    public void incrementStock(int quantity) {
        this.stockQuantity += quantity;
    }
}