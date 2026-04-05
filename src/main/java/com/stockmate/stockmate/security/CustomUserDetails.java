package com.stockmate.stockmate.security;


import com.stockmate.stockmate.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CustomUserDetails — bridges our {@link User} JPA entity with Spring
 * Security's {@link UserDetails} contract.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  WHY THIS CLASS EXISTS                                          │
 * │  Spring Security does not know about our User entity.           │
 * │  This wrapper teaches it how to read credentials, roles, and   │
 * │  account state from our domain object without coupling Spring   │
 * │  Security directly to JPA.                                      │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * WHAT IT WRAPS
 * ─────────────
 *   User.password  → getPassword()     (BCrypt hash stored in DB)
 *   User.username  → getUsername()     (login identifier)
 *   User.enabled   → isEnabled()       (Admin can soft-disable accounts)
 *   User.roles     → getAuthorities()  (ROLE_ADMIN / ROLE_SELLER / ROLE_BUYER)
 *
 * HOW IT IS USED
 * ──────────────
 *   CustomUserDetailsService instantiates this per login attempt.
 *   After authentication the object lives in the SecurityContext and can
 *   be retrieved in controllers via Authentication.getPrincipal().
 *
 *   Example in a controller:
 *     CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
 *     Long userId = principal.getUser().getId();
 *
 * NOT a Spring bean — created per request by CustomUserDetailsService.
 */
public class CustomUserDetails implements UserDetails {

    /** The wrapped entity — accessible via getUser() for callers needing the id. */
    private final User user;

    /**
     * Pre-computed authority set. Built once at construction time.
     * Spring Security reads getAuthorities() on every authorisation check,
     * so we avoid repeated stream operations.
     */
    private final Set<GrantedAuthority> authorities;

    // ── Constructor ───────────────────────────────────────────

    /**
     * @param user fully-loaded JPA entity; roles must already be initialised
     *             (User.roles is EAGER-fetched, so this is guaranteed).
     */
    public CustomUserDetails(User user) {
        this.user = user;

        // Map "ROLE_ADMIN" / "ROLE_SELLER" / "ROLE_BUYER" → SimpleGrantedAuthority
        // Spring Security uses these names in hasRole() / @PreAuthorize.
        this.authorities = user.getRoles()
                .stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toUnmodifiableSet());
    }

    // ── UserDetails contract ──────────────────────────────────

    /** Used by Spring Security for @PreAuthorize and SecurityConfig matchers. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * Returns the BCrypt hash stored in the DB.
     * Spring Security calls PasswordEncoder.matches(rawPassword, hash) —
     * the plaintext submitted at login is NEVER stored anywhere.
     */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /** Login identifier — must match exactly what was stored at registration. */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * Account never expires — expiry is not a PRD requirement.
     * Admin manages access via the enabled flag only.
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * No locking mechanism in this system.
     * Disabled accounts are handled via isEnabled() only.
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** No password-rotation requirement in this project. */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Delegates to User.enabled.
     * When Admin calls UserService.disableUser(), this returns false and
     * Spring Security rejects the login attempt automatically.
     */
    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    // ── StockMate-specific accessor ───────────────────────────

    /**
     * Exposes the underlying User entity for cases where the controller or
     * service needs the user id or email without an extra DB query.
     */
    public User getUser() {
        return user;
    }
}