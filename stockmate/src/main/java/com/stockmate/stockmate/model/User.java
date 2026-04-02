package com.stockmate.stockmate.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a registered user — ADMIN, SELLER, or BUYER.
 * Roles are assigned via the user_roles join table (M:M).
 *
 * Rules enforced here:
 * - username and email are unique
 * - password stores only the BCrypt hash (never plaintext)
 * - enabled flag allows Admin to deactivate accounts
 * - createdAt is set automatically on first persist
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;   // BCrypt hash — never plaintext

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * M:M with roles via user_roles join table.
     * EAGER fetch — Spring Security loads roles on every authentication check.
     * Using Set<Role> avoids duplicates.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns        = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    // ── Lifecycle ─────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────

    protected User() {
        // JPA required
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email    = email;
        this.password = password;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long getId()                  { return id; }
    public String getUsername()          { return username; }
    public String getEmail()             { return email; }
    public String getPassword()          { return password; }
    public boolean isEnabled()           { return enabled; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public Set<Role> getRoles()          { return roles; }

    // ── Setters ───────────────────────────────────────────────

    public void setUsername(String username)    { this.username = username; }
    public void setEmail(String email)          { this.email = email; }
    public void setPassword(String password)    { this.password = password; }
    public void setEnabled(boolean enabled)     { this.enabled = enabled; }
    public void setRoles(Set<Role> roles)       { this.roles = roles; }

    // ── Helpers ───────────────────────────────────────────────

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }
}