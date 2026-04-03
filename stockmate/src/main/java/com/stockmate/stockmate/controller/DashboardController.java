package com.stockmate.stockmate.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.stockmate.stockmate.dto.response.OrderResponse;
import com.stockmate.stockmate.dto.response.ProductResponse;
import com.stockmate.stockmate.security.CustomUserDetails;
import com.stockmate.stockmate.service.OrderService;
import com.stockmate.stockmate.service.ProductService;

import lombok.extern.slf4j.Slf4j;

/**
 * DashboardController — single entry-point that routes /dashboard to the
 * correct role-specific dashboard page.
 *
 * After login, SecurityConfig sends users to /products (the catalogue).
 * After registration, AuthController sends users to /products too.
 * This controller handles any explicit /dashboard request and redirects
 * to the page appropriate for the user's role.
 *
 * Seller and buyer pages include summary data, so ProductService and
 * OrderService are injected for dashboard view models.
 */
@Slf4j
@Controller
public class DashboardController {

    private final ProductService productService;
    private final OrderService orderService;

    public DashboardController(ProductService productService,
                               OrderService orderService) {
        this.productService = productService;
        this.orderService = orderService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSeller = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SELLER"));

        if (isAdmin)  return "redirect:/admin";
        if (isSeller) return "redirect:/dashboard/seller";
        return "redirect:/dashboard/buyer";
    }

    /** Seller-specific dashboard — products + incoming orders summary. */
    @GetMapping("/dashboard/seller")
    public String sellerDashboard(@AuthenticationPrincipal CustomUserDetails currentUser,
                                  Model model) {
        List<ProductResponse> sellerProducts =
                productService.getProductsBySeller(currentUser.getUsername());
        List<OrderResponse> sellerOrders =
                orderService.getOrdersBySeller(currentUser.getUsername());

        long pendingOrders = sellerOrders.stream()
                .filter(o -> "PENDING".equals(o.status()))
                .count();

        model.addAttribute("totalProducts", sellerProducts.size());
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("products", sellerProducts.stream().limit(5).toList());
        model.addAttribute("recentOrders", sellerOrders.stream().limit(5).toList());
        return "dashboard/seller";   // → templates/dashboard/seller.html
    }

    /** Buyer-specific dashboard — recent orders + browse shortcut. */
    @GetMapping("/dashboard/buyer")
    public String buyerDashboard(@AuthenticationPrincipal CustomUserDetails currentUser,
                                 Model model) {
        List<OrderResponse> buyerOrders =
                orderService.getOrdersByBuyer(currentUser.getUsername());

        long activeOrders = buyerOrders.stream()
                .filter(o -> "PENDING".equals(o.status())
                        || "CONFIRMED".equals(o.status())
                        || "SHIPPED".equals(o.status()))
                .count();

        model.addAttribute("totalOrders", buyerOrders.size());
        model.addAttribute("activeOrders", activeOrders);
        model.addAttribute("recentOrders", buyerOrders.stream().limit(5).toList());
        return "dashboard/buyer";    // → templates/dashboard/buyer.html
    }
}