package com.stockmate.stockmate.service;

import com.stockmate.stockmate.dto.request.RegisterRequest;
import com.stockmate.stockmate.dto.response.UserResponse;

import java.util.List;

/**
 * UserService — all user-lifecycle business rules.
 *
 * Covers: registration, role change (Admin), account disable/delete (Admin),
 * and listing users (Admin dashboard).
 *
 * Intentionally does NOT include login — Spring Security calls
 * CustomUserDetailsService.loadUserByUsername() directly; that is not a
 * business-rule operation, so it lives in the security package.
 */
public interface UserService {

    // ── Registration (FR-AUTH-01 / FR-AUTH-04) ────────────────

    /**
     * Register a new user with role BUYER or SELLER (user's choice at sign-up).
     *
     * Inputs     : RegisterRequest (username, email, password, role)
     * Validation : username unique · email unique · role must be BUYER or SELLER
     *              (ADMIN role cannot be self-assigned — FR-AUTH-04)
     * Repos      : UserRepository (exists checks + save) · RoleRepository (via RoleService)
     * Business   : BCrypt-hash the password before persisting
     * Output     : UserResponse DTO (no password field)
     */
    UserResponse register(RegisterRequest request);

    // ── Admin: list users (FR-ADM-01) ────────────────────────

    /**
     * Return all registered users — ADMIN only.
     *
     * Inputs     : none
     * Validation : @PreAuthorize("hasRole('ADMIN')") on impl
     * Repos      : UserRepository.findAll()
     * Business   : none
     * Output     : List<UserResponse>
     */
    List<UserResponse> getAllUsers();

    // ── Admin: change role (FR-ADM-02) ───────────────────────

    /**
     * Promote or change the role of a user — ADMIN only.
     *
     * Inputs     : userId (Long), newRoleName (String, e.g. "ROLE_SELLER")
     * Validation : user must exist · role must exist · @PreAuthorize ADMIN
     * Repos      : UserRepository · RoleRepository (via RoleService)
     * Business   : replace all existing roles with the single new role
     * Output     : updated UserResponse
     */
    UserResponse changeRole(Long userId, String newRoleName);

    // ── Admin: disable / delete user (FR-ADM-03) ─────────────

    /**
     * Disable a user account — ADMIN only.
     * Soft-disable: sets enabled = false rather than deleting the row
     * (preserves referential integrity with orders/products).
     *
     * Inputs     : userId (Long)
     * Validation : user must exist · @PreAuthorize ADMIN
     * Repos      : UserRepository
     * Business   : enabled flag → false; user can no longer log in
     * Output     : void
     */
    void disableUser(Long userId);

    /**
     * Permanently delete a user account — ADMIN only.
     * Only safe if the user has no orders or products; caller is
     * responsible for ensuring referential integrity first, or
     * the DB FK constraint will throw.
     *
     * Inputs     : userId (Long)
     * Validation : user must exist · @PreAuthorize ADMIN
     * Repos      : UserRepository
     * Business   : hard delete
     * Output     : void
     */
    void deleteUser(Long userId);

    // ── Internal helper (used by security layer) ─────────────

    /**
     * Find a user by username — used by CustomUserDetailsService.
     *
     * Inputs     : username (String)
     * Validation : throws ResourceNotFoundException if not found
     * Repos      : UserRepository.findByUsername()
     * Business   : none
     * Output     : UserResponse
     */
    UserResponse findByUsername(String username);
}