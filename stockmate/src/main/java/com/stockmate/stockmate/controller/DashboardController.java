package com.stockmate.stockmate.controller;

import com.stockmate.stockmate.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * DashboardController — single entry-point that routes /dashboard to the
 * correct role-specific dashboard page.
 *
 * After login, SecurityConfig sends users to /products (the catalogue).
 * After registration, AuthController sends users to /products too.
 * This controller handles any explicit /dashboard request and redirects
 * to the page appropriate for the user's role.
 *
 * No service injection needed — just a role check on the principal.
 */
@Slf4j
@Controller
public class DashboardController {

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
    public String sellerDashboard() {
        return "dashboard/seller";   // → templates/dashboard/seller.html
    }

    /** Buyer-specific dashboard — recent orders + browse shortcut. */
    @GetMapping("/dashboard/buyer")
    public String buyerDashboard() {
        return "dashboard/buyer";    // → templates/dashboard/buyer.html
    }
}