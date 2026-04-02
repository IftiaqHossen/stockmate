package com.stockmate.stockmate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Product category managed exclusively by ADMIN.
 * One Category → Many Products (1:M).
 *
 * The products collection is LAZY — we never need all products
 * just because we loaded a category. Service layer fetches
 * products separately when needed.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;   // nullable

    /**
     * Inverse side of the Product → Category relationship.
     * mappedBy = "category" refers to the field name in Product.java.
     * LAZY — not loaded unless explicitly accessed.
     */
    @OneToMany(mappedBy = "category", fetch = jakarta.persistence.FetchType.LAZY)
    private List<Product> products = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────

    protected Category() {
        // JPA required
    }

    public Category(String name, String description) {
        this.name        = name;
        this.description = description;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long getId()               { return id; }
    public String getName()           { return name; }
    public String getDescription()    { return description; }
    public List<Product> getProducts(){ return products; }

    // ── Setters ───────────────────────────────────────────────

    public void setName(String name)              { this.name = name; }
    public void setDescription(String description){ this.description = description; }
}