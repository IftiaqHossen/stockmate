package com.stockmate.stockmate.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stockmate.stockmate.dto.request.PlaceOrderRequest;
import com.stockmate.stockmate.dto.response.OrderResponse;
import com.stockmate.stockmate.exception.InsufficientStockException;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.model.enums.OrderStatus;
import com.stockmate.stockmate.security.CustomUserDetails;
import com.stockmate.stockmate.service.OrderService;

/**
 * Integration tests for OrderController.
 *
 * Orders are the most sensitive transactional flow in StockMate — they modify
 * stock, involve multiple roles, and have real financial implications. Every
 * test here models a realistic production scenario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    // ── TC-1: placeOrder_asBuyer_success ──────────────────────────────────────
    /**
     * WHY: The core happy path — BUYER places a valid order. Validates: 201
     * status, JSON response with PENDING status, correct product linkage.
     */
    @Test
    @DisplayName("POST /orders: BUYER places valid order receives 201 with PENDING status")
    void placeOrder_asBuyer_success() throws Exception {
        // Arrange
        PlaceOrderRequest request = new PlaceOrderRequest(10L, 2);

        OrderResponse response = buildOrderResponse(1L, 10L, 2, "1999.98", OrderStatus.PENDING);
        given(orderService.placeOrder(any(PlaceOrderRequest.class), eq("buyerUser")))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/orders")
                .with(user(buildPrincipal("buyerUser", "ROLE_BUYER")))
                .with(csrf())
                .param("productId", String.valueOf(request.productId()))
                .param("quantity", String.valueOf(request.quantity())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/my"))
                .andExpect(flash().attributeExists("successMessage"));

        then(orderService).should().placeOrder(any(PlaceOrderRequest.class), eq("buyerUser"));
    }

    // ── TC-2: buyerOrders_asSeller_forbidden ─────────────────────────────────
    /**
     * WHY: /orders/my is buyer-only in URL-level security.
     */
    @Test
    @DisplayName("GET /orders/my: SELLER receives 403 Forbidden")
    void buyerOrders_asSeller_forbidden() throws Exception {
        mockMvc.perform(get("/orders/my")
                .with(user("sellerUser").roles("SELLER")))
                .andExpect(status().isForbidden());
    }

    // ── TC-3: placeOrder_insufficientStock ────────────────────────────────────
    /**
     * WHY: The most common order failure in production. GlobalExceptionHandler
     * must translate InsufficientStockException → 409 Conflict so the client
     * can display a meaningful "out of stock" message.
     */
    @Test
    @DisplayName("POST /orders: insufficient stock returns 409 Conflict via GlobalExceptionHandler")
    void placeOrder_insufficientStock() throws Exception {
        // Arrange
        PlaceOrderRequest request = new PlaceOrderRequest(10L, 100);

        given(orderService.placeOrder(any(PlaceOrderRequest.class), eq("buyerUser")))
                .willThrow(new InsufficientStockException("Not enough stock for product id 10"));

        // Act & Assert
        mockMvc.perform(post("/orders")
                .with(user(buildPrincipal("buyerUser", "ROLE_BUYER")))
                .with(csrf())
                .param("productId", String.valueOf(request.productId()))
                .param("quantity", String.valueOf(request.quantity())))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/400"));
    }

    // ── TC-4: cancelOrder_asBuyer_success ─────────────────────────────────────
    /**
     * WHY: BUYER cancelling their own order must return 200 OK (FR-ORD-01).
     * This also indirectly tests that the stock restoration service call was
     * made.
     */
    @Test
    @DisplayName("PUT /orders/{id}/cancel: BUYER cancels own order receives 200 OK")
    void cancelOrder_asBuyer_success() throws Exception {
        // Arrange
        OrderResponse cancelled = buildOrderResponse(1L, 10L, 2, "1999.98", OrderStatus.CANCELLED);
        given(orderService.cancelOrder(1L, "buyerUser")).willReturn(cancelled);

        // Act & Assert
        mockMvc.perform(put("/orders/1/cancel")
                .with(user(buildPrincipal("buyerUser", "ROLE_BUYER")))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/my"))
                .andExpect(flash().attributeExists("successMessage"));

        then(orderService).should().cancelOrder(1L, "buyerUser");
    }

    // ── TC-5: cancelOrder_notFound ────────────────────────────────────────────
    /**
     * WHY: Buyer tries to cancel an order that doesn't exist (deleted, wrong
     * ID). Must return 404, not 500. Tests GlobalExceptionHandler path for
     * orders.
     */
    @Test
    @DisplayName("PUT /orders/{id}/cancel: non-existent order returns 404")
    void cancelOrder_notFound() throws Exception {
        // Arrange
        given(orderService.cancelOrder(9999L, "buyerUser"))
                .willThrow(new ResourceNotFoundException("Order not found with id: 9999"));

        // Act & Assert
        mockMvc.perform(put("/orders/9999/cancel")
                .with(user(buildPrincipal("buyerUser", "ROLE_BUYER")))
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    // ── TC-6: getMyOrders_asBuyer ─────────────────────────────────────────────
    /**
     * WHY: BUYER must be able to retrieve their order history (FR-ORD-04).
     * Validates endpoint accessibility and JSON array response structure.
     */
    @Test
    @DisplayName("GET /orders/my: BUYER receives 200 with their order list")
    void getMyOrders_asBuyer() throws Exception {
        // Arrange
        OrderResponse response = buildOrderResponse(1L, 10L, 2, "1999.98", OrderStatus.PENDING);
        given(orderService.getOrdersByBuyer("buyerUser")).willReturn(List.of(response));

        // Act & Assert
        mockMvc.perform(get("/orders/my")
                .with(user(buildPrincipal("buyerUser", "ROLE_BUYER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/buyer-orders"))
                .andExpect(model().attributeExists("orders", "placeOrderRequest"));
    }

    // ── TC-7: getAllOrders_asAdmin ─────────────────────────────────────────────
    /**
     * WHY: Admin must see ALL system orders (FR-ORD-06). Also validates that a
     * non-ADMIN (BUYER) trying the same endpoint gets 403.
     */
    @Test
    @DisplayName("GET /orders: ADMIN receives 200 with all orders")
    void getAllOrders_asAdmin() throws Exception {
        // Arrange
        OrderResponse o1 = buildOrderResponse(1L, 10L, 2, "1999.98", OrderStatus.PENDING);
        OrderResponse o2 = buildOrderResponse(2L, 11L, 1, "49.99", OrderStatus.CONFIRMED);
        given(orderService.getAllOrders()).willReturn(List.of(o1, o2));

        // Act & Assert
        mockMvc.perform(get("/orders")
                .with(user("adminUser").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/all-orders"))
                .andExpect(model().attributeExists("orders", "updateStatusRequest"));
    }

    @Test
    @DisplayName("GET /orders: BUYER trying admin endpoint receives 403 Forbidden")
    void getAllOrders_asBuyer_forbidden() throws Exception {
        mockMvc.perform(get("/orders")
                .with(user("buyerUser").roles("BUYER")))
                .andExpect(status().isForbidden());
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private OrderResponse buildOrderResponse(Long id, Long productId, int qty, String total, OrderStatus status) {
        return new OrderResponse(
                id,
                501L,
                "buyerUser",
                productId,
                "Laptop",
                "sellerUser",
                qty,
                new BigDecimal(total),
                status.name(),
                LocalDateTime.now()
        );
    }

    private CustomUserDetails buildPrincipal(String username, String roleName) {
        User user = new User(username, username + "@test.com", "encoded-password");
        user.setRoles(Set.of(new Role(roleName)));
        return new CustomUserDetails(user);
    }
}
