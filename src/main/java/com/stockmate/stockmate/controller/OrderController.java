package com.stockmate.stockmate.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.stockmate.stockmate.dto.request.PlaceOrderRequest;
import com.stockmate.stockmate.dto.request.UpdateOrderStatusRequest;
import com.stockmate.stockmate.dto.response.OrderResponse;
import com.stockmate.stockmate.security.CustomUserDetails;
import com.stockmate.stockmate.service.OrderService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * OrderController — order lifecycle for BUYER, SELLER, and ADMIN.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * RESPONSIBILITY BOUNDARY (CLAUDE.md)
 * ══════════════════════════════════════════════════════════════════════════ ✅
 * Maps URLs → OrderService calls ✅ Extracts username from SecurityContext via
 * @AuthenticationPrincipal ✅ Passes validated DTOs to OrderService ❌ No
 * business logic — stock validation, decrement, ownership → service layer ❌
 * Never touches a repository directly ❌ Never returns a JPA entity
 *
 * ACCESS CONTROL (two-layer) ────────────────────────── Layer 1
 * (SecurityConfig): POST /orders → BUYER GET /orders/my → BUYER GET
 * /orders/seller → SELLER GET /orders → ADMIN PUT /orders/{id}/status → SELLER
 * or ADMIN PUT /orders/{id}/cancel → BUYER Layer 2 (@PreAuthorize in
 * OrderServiceImpl): Ownership checks — BUYER can only cancel their own order,
 * SELLER can only update status for their own product's orders.
 *
 * ENDPOINT SUMMARY
 * ──────────────────────────────────────────────────────────────── POST /orders
 * → place order (BUYER) FR-ORD-01/02/03 PUT /orders/{id}/cancel → cancel order
 * (BUYER) FR-ORD-01 GET /orders/my → buyer's order history FR-ORD-04 GET
 * /orders/seller → seller's incoming orders FR-ORD-05 GET /orders → all orders
 * (ADMIN) FR-ORD-06 PUT /orders/{id}/status → update status (SELLER/ADM)
 * FR-ORD-07
 *
 * HTML FORM NOTE: PUT uses Spring's HiddenHttpMethodFilter. Requires:
 * spring.mvc.hiddenmethod.filter.enabled=true
 */
@Slf4j
@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    // ── Constructor injection ─────────────────────────────────
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ══════════════════════════════════════════════════════════
    //  POST /orders — place a new order (BUYER)
    //  FR-ORD-01 / FR-ORD-02 / FR-ORD-03
    // ══════════════════════════════════════════════════════════
    /**
     * Places a new order.
     *
     * The form lives on the product detail page (/products/{id}). On success →
     * redirect to /orders/my (buyer's order list).
     *
     * InsufficientStockException → GlobalExceptionHandler → 409 page.
     * ResourceNotFoundException → GlobalExceptionHandler → 404 page.
     *
     * BindingResult errors (e.g. quantity < 1) redirect back to the product
     * detail page with a flash error.
     */
    @PostMapping
    public String placeOrder(
            @Valid @ModelAttribute("placeOrderRequest") PlaceOrderRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            // Return to the product detail page with an error
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Invalid order: please enter a quantity of at least 1.");
            return "redirect:/products/" + request.productId();
        }

        OrderResponse order = orderService.placeOrder(request, currentUser.getUsername());

        log.info("Order placed: id={} by buyer={}, product={}, qty={}",
                order.id(), currentUser.getUsername(),
                order.productName(), order.quantity());

        redirectAttrs.addFlashAttribute("successMessage",
                "Order placed successfully! Order #" + order.id());
        return "redirect:/orders/my";
    }

    // ══════════════════════════════════════════════════════════
    //  PUT /orders/{id}/cancel — cancel an order (BUYER)
    //  FR-ORD-01
    //
    //  HTML workaround: <input type="hidden" name="_method" value="put"/>
    //  (We use PUT not DELETE because cancellation is a status change,
    //   not a hard delete — the order record is preserved for history.)
    // ══════════════════════════════════════════════════════════
    /**
     * Cancels an order and restores product stock.
     *
     * AccessDeniedException (trying to cancel another buyer's order) →
     * GlobalExceptionHandler → 403 page. IllegalStateException (order already
     * DELIVERED or CANCELLED) → caught here for a friendly flash message
     * (better UX than an error page).
     */
    @PutMapping("/{id}/cancel")
    public String cancelOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttrs) {

        try {
            OrderResponse cancelled = orderService.cancelOrder(id, currentUser.getUsername());
            log.info("Order cancelled: id={} by buyer={}", cancelled.id(), currentUser.getUsername());
            redirectAttrs.addFlashAttribute("successMessage",
                    "Order #" + cancelled.id() + " cancelled. Stock has been restored.");

        } catch (IllegalStateException ex) {
            log.warn("Order cancel blocked: {}", ex.getMessage());
            redirectAttrs.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/orders/my";
    }

    // ══════════════════════════════════════════════════════════
    //  GET /orders/my — buyer views their own orders (BUYER)
    //  FR-ORD-04
    // ══════════════════════════════════════════════════════════
    /**
     * Shows the buyer's personal order history, newest first. URL-level guard
     * in SecurityConfig restricts this to BUYER.
     */
    @GetMapping("/my")
    public String buyerOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        List<OrderResponse> orders
                = orderService.getOrdersByBuyer(currentUser.getUsername());

        model.addAttribute("orders", orders);
        // Empty DTO for the place-order form if it's embedded on this page
        model.addAttribute("placeOrderRequest", new PlaceOrderRequest(null, 1));
        return "orders/buyer-orders";   // → templates/orders/buyer-orders.html
    }

    // ══════════════════════════════════════════════════════════
    //  GET /orders/seller — seller views orders for their products (SELLER)
    //  FR-ORD-05
    // ══════════════════════════════════════════════════════════
    /**
     * Shows all orders placed for the authenticated seller's products.
     * URL-level guard in SecurityConfig restricts this to SELLER.
     */
    @GetMapping("/seller")
    public String sellerOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        List<OrderResponse> orders
                = orderService.getOrdersBySeller(currentUser.getUsername());

        model.addAttribute("orders", orders);
        model.addAttribute("updateStatusRequest", new UpdateOrderStatusRequest(null));
        return "orders/seller-orders";   // → templates/orders/seller-orders.html
    }

    // ══════════════════════════════════════════════════════════
    //  GET /orders — admin views all orders (ADMIN)
    //  FR-ORD-06
    // ══════════════════════════════════════════════════════════
    /**
     * Shows all orders system-wide. URL-level guard in SecurityConfig restricts
     * GET /orders to ADMIN.
     */
    @GetMapping
    public String allOrders(Model model) {
        List<OrderResponse> orders = orderService.getAllOrders();
        model.addAttribute("orders", orders);
        model.addAttribute("updateStatusRequest", new UpdateOrderStatusRequest(null));
        return "orders/all-orders";   // → templates/orders/all-orders.html
    }

    // ══════════════════════════════════════════════════════════
    //  PUT /orders/{id}/status — update order status (SELLER / ADMIN)
    //  FR-ORD-07  PENDING → CONFIRMED → SHIPPED → DELIVERED
    //
    //  HTML workaround: <input type="hidden" name="_method" value="put"/>
    // ══════════════════════════════════════════════════════════
    /**
     * Updates an order's status.
     *
     * Ownership check for SELLER (may only update their product's orders) lives
     * in OrderServiceImpl — AccessDeniedException → GlobalExceptionHandler →
     * 403.
     *
     * Terminal state guard (DELIVERED / CANCELLED) caught here for friendly
     * flash.
     *
     * After update, SELLER is redirected to /orders/seller, ADMIN is redirected
     * to /orders.
     */
    @PutMapping("/{id}/status")
    public String updateOrderStatus(
            @PathVariable Long id,
            @Valid @ModelAttribute("updateStatusRequest") UpdateOrderStatusRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Invalid status value. Please select a valid order status.");
            return determineOrdersRedirect(currentUser);
        }

        try {
            OrderResponse updated = orderService.updateOrderStatus(
                    id, request, currentUser.getUsername());

            log.info("Order status updated: id={} → {} by user={}",
                    updated.id(), updated.status(), currentUser.getUsername());

            redirectAttrs.addFlashAttribute("successMessage",
                    "Order #" + updated.id() + " status updated to " + updated.status() + ".");

        } catch (IllegalStateException ex) {
            log.warn("Order status update blocked: {}", ex.getMessage());
            redirectAttrs.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return determineOrdersRedirect(currentUser);
    }

    // ── Private helper ────────────────────────────────────────
    /**
     * After a status update, redirect SELLER to /orders/seller and ADMIN to
     * /orders — each role returns to their own view.
     */
    private String determineOrdersRedirect(CustomUserDetails user) {
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin ? "redirect:/orders" : "redirect:/orders/seller";
    }
}
