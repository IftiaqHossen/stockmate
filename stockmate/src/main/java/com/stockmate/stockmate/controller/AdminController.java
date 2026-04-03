package com.stockmate.stockmate.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.stockmate.stockmate.dto.request.ChangeRoleRequest;
import com.stockmate.stockmate.dto.response.OrderResponse;
import com.stockmate.stockmate.dto.response.UserResponse;
import com.stockmate.stockmate.service.OrderService;
import com.stockmate.stockmate.service.UserService;

import lombok.extern.slf4j.Slf4j;

/**
 * AdminController — admin-only operations: dashboard, user management.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * RESPONSIBILITY BOUNDARY (CLAUDE.md)
 * ══════════════════════════════════════════════════════════════════════════
 *   ✅ Maps /admin/** URLs → service calls
 *   ✅ Aggregates data from UserService + OrderService for the dashboard
 *   ❌ No business logic — role assignment, disable/delete logic → service
 *   ❌ Never touches a repository directly
 *   ❌ Never returns a JPA entity
 *
 * ACCESS CONTROL (two-layer)
 * ──────────────────────────
 *   Layer 1 (SecurityConfig):
 *     /admin/**  → hasRole('ADMIN')   — blanket URL guard
 *   Layer 2 (@PreAuthorize in UserServiceImpl):
 *     getAllUsers / changeRole / disableUser / deleteUser → hasRole('ADMIN')
 *   Double enforcement ensures a misconfigured URL rule cannot bypass service logic.
 *
 * ENDPOINT SUMMARY  (FR-ADM-01 / FR-ADM-02 / FR-ADM-03)
 * ──────────────────────────────────────────────────────
 *   GET    /admin              → admin dashboard (user + order counts)
 *   GET    /admin/users        → list all users
 *   PUT    /admin/users/{id}/role    → change user role (ADMIN)
 *   PUT    /admin/users/{id}/disable → disable user account (ADMIN)
 *   DELETE /admin/users/{id}         → hard delete user (ADMIN)
 *
 * HTML FORM NOTE:
 *   PUT and DELETE use Spring's HiddenHttpMethodFilter.
 *   Requires: spring.mvc.hiddenmethod.filter.enabled=true
 */
@Slf4j
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService  userService;
    private final OrderService orderService;

    // ── Constructor injection ─────────────────────────────────

    public AdminController(UserService userService, OrderService orderService) {
        this.userService  = userService;
        this.orderService = orderService;
    }

    // ══════════════════════════════════════════════════════════
    //  GET /admin — admin dashboard
    // ══════════════════════════════════════════════════════════

    /**
     * Admin landing page with summary statistics.
     *
     * Aggregates:
     *   - Total registered users count
     *   - Total orders count
     *   - Full orders list (for the orders table on the dashboard)
     *
     * Each service call is independent — no business logic mixed in.
     */
    @GetMapping
    public String dashboard(Model model) {
        List<UserResponse>  users  = userService.getAllUsers();
        List<OrderResponse> orders = orderService.getAllOrders();

        model.addAttribute("totalUsers",  users.size());
        model.addAttribute("totalOrders", orders.size());
        model.addAttribute("recentOrders", orders.stream().limit(10).toList());
        // Recent 10 orders for the dashboard summary table

        return "admin/dashboard";   // → templates/admin/dashboard.html
    }

    // ══════════════════════════════════════════════════════════
    //  GET /admin/users — list all registered users
    //  FR-ADM-01
    // ══════════════════════════════════════════════════════════

    /**
     * Shows the full user list for admin management.
     * Business rule (ADMIN only) is enforced by @PreAuthorize in UserServiceImpl.
     */
    @GetMapping("/users")
    public String listUsers(Model model) {
        List<UserResponse> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("roleChangeRequest", new ChangeRoleRequest("ROLE_BUYER"));
        return "admin/users";   // → templates/admin/users.html
    }

    // ══════════════════════════════════════════════════════════
    //  PUT /admin/users/{id}/role — change user role (ADMIN)
    //  FR-ADM-02
    //
    //  HTML workaround: <input type="hidden" name="_method" value="put"/>
    // ══════════════════════════════════════════════════════════

    /**
     * Changes the role of a user account.
     *
     * Accepted values for newRole: "ROLE_BUYER", "ROLE_SELLER", "ROLE_ADMIN".
     * UserService.changeRole() validates the role exists via RoleService.
     *
     * ResourceNotFoundException (unknown userId) → GlobalExceptionHandler → 404.
     */
    @PutMapping("/users/{id}/role")
    public String changeUserRole(
            @PathVariable Long id,
            @RequestParam("newRole") String newRole,
            RedirectAttributes redirectAttrs) {

        UserResponse updated = userService.changeRole(id, newRole);

        log.info("Admin changed role: userId={} → {}", id, newRole);

        redirectAttrs.addFlashAttribute("successMessage",
                "User '" + updated.username() + "' role changed to " + newRole + ".");
        return "redirect:/admin/users";
    }

    // ══════════════════════════════════════════════════════════
    //  PUT /admin/users/{id}/disable — soft-disable a user (ADMIN)
    //  FR-ADM-03
    //
    //  HTML workaround: <input type="hidden" name="_method" value="put"/>
    //
    //  Soft-disable is preferred over hard-delete in most cases because
    //  it preserves the user's orders and product history (FK integrity).
    // ══════════════════════════════════════════════════════════

    /**
     * Disables a user account (enabled = false).
     * The user can no longer log in.
     * Their orders and products remain in the DB — referential integrity preserved.
     */
    @PutMapping("/users/{id}/disable")
    public String disableUser(@PathVariable Long id,
                              RedirectAttributes redirectAttrs) {

        userService.disableUser(id);

        log.info("Admin disabled userId={}", id);

        redirectAttrs.addFlashAttribute("successMessage",
                "User account disabled successfully.");
        return "redirect:/admin/users";
    }

    // ══════════════════════════════════════════════════════════
    //  DELETE /admin/users/{id} — hard-delete a user (ADMIN)
    //  FR-ADM-03
    //
    //  HTML workaround: <input type="hidden" name="_method" value="delete"/>
    //
    //  WARNING: This will fail with a DB FK constraint exception if the user
    //  has associated orders or products. Soft-disable (/disable) is safer.
    //  Catch the DataIntegrityViolationException from Spring and show a message.
    // ══════════════════════════════════════════════════════════

    /**
     * Hard-deletes a user account.
     *
     * DataIntegrityViolationException (user has orders/products)
     *   is caught here with a friendly flash error — the record cannot be
     *   deleted until its dependents are removed first.
     *   In that case the admin should use /disable instead.
     */
    @DeleteMapping("/users/{id}")
    public String deleteUser(@PathVariable Long id,
                             RedirectAttributes redirectAttrs) {

        try {
            userService.deleteUser(id);
            log.info("Admin deleted userId={}", id);
            redirectAttrs.addFlashAttribute("successMessage",
                    "User account permanently deleted.");

        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // User has linked orders or products — FK constraint prevents deletion
            log.warn("Cannot delete userId={} — FK constraint: {}", id, ex.getMessage());
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Cannot delete this user — they have existing orders or products. " +
                            "Use 'Disable' instead to deactivate the account.");
        }

        return "redirect:/admin/users";
    }
}