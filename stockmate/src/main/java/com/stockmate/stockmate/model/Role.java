package com.stockmate.stockmate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lookup entity for user roles.
 * Three rows always exist: ROLE_ADMIN, ROLE_SELLER, ROLE_BUYER.
 * Seeded via SQL script — never created programmatically.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 20)
    private String name;   // ROLE_ADMIN | ROLE_SELLER | ROLE_BUYER

    // ── Constructors ──────────────────────────────────────────

    protected Role() {
        // JPA required
    }

    public Role(String name) {
        this.name = name;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    // ── Setters ───────────────────────────────────────────────

    public void setName(String name) {
        this.name = name;
    }
}