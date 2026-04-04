package com.stockmate.stockmate.controller;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.stockmate.stockmate.dto.request.LoginRequest;
import com.stockmate.stockmate.dto.request.RegisterRequest;
import com.stockmate.stockmate.dto.response.UserResponse;
import com.stockmate.stockmate.service.UserService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * AuthController — handles user registration, login page display, and logout.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * RESPONSIBILITY BOUNDARY (CLAUDE.md)
 * ══════════════════════════════════════════════════════════════════════════ ✅
 * Maps URLs → service calls ✅ Passes validated DTOs to UserService ✅ Adds flash
 * messages / model attributes for Thymeleaf views ❌ Zero business logic — no
 * password hashing, no role lookup, no DB calls ❌ Never touches a repository
 * directly ❌ Never returns a JPA entity
 *
 * LOGIN / LOGOUT note: Spring Security handles the actual POST /auth/login
 * authentication automatically via DaoAuthenticationProvider + formLogin() in
 * SecurityConfig. This controller only needs to show the login GET page. Logout
 * is similarly handled by Spring Security's logout filter. We still handle
 * auto-login after registration here programmatically.
 *
 * ENDPOINT SUMMARY ──────────────── GET /auth/register → show registration form
 * POST /auth/register → submit registration, auto-login, redirect to /products
 * GET /auth/login → show login form (Spring Security reads ?error and ?logout
 * params)
 */
@Slf4j
@Controller
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    // ── Constructor injection — no @Autowired on fields ──────
    public AuthController(UserService userService,
            AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    // ══════════════════════════════════════════════════════════
    //  GET /auth/register — show the registration form
    // ══════════════════════════════════════════════════════════
    /**
     * Renders the registration form. Adds an empty RegisterRequest to the model
     * so Thymeleaf's th:object and th:field can bind form fields automatically.
     */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        // Provide an empty DTO for the form binding
        model.addAttribute("registerRequest", new RegisterRequest("", "", "", "ROLE_BUYER"));
        return "auth/register";   // → templates/auth/register.html
    }

    // ══════════════════════════════════════════════════════════
    //  POST /auth/register — process the registration
    // ══════════════════════════════════════════════════════════
    /**
     * Handles registration form submission.
     *
     * Flow: 1. @Valid validates the DTO (username/email/password/role
     * constraints) 2. BindingResult catches field-level errors → redisplay form
     * with messages 3. UserService.register() — BCrypt hash, duplicate checks,
     * persist 4. Programmatic auto-login via AuthenticationManager (user does
     * not have to log in again after registering) 5. Redirect to product
     * catalogue
     *
     * If UserService throws IllegalArgumentException (duplicate
     * username/email), GlobalExceptionHandler maps it to 400 — but for a
     * Thymeleaf form it's better UX to catch it here and redisplay the form
     * with the error message.
     *
     * @param request validated DTO from the form
     * @param bindingResult field-level constraint violations from @Valid
     * @param model passed back to the view on validation failure
     * @param redirectAttrs flash message sent to the product catalogue on
     * success
     */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttrs) {

        // ── Step 1: bean-validation field errors ──────────────
        if (bindingResult.hasErrors()) {
            log.debug("Registration form has {} validation error(s)", bindingResult.getErrorCount());
            return "auth/register";  // redisplay form; Thymeleaf shows field errors
        }

        // ── Step 2: delegate to service layer ────────────────
        try {
            UserResponse saved = userService.register(request);
            log.info("New user registered: id={}, username={}", saved.id(), saved.username());

            // ── Step 3: auto-login after registration ─────────
            // Authenticate the new user programmatically so they land on the
            // catalogue already logged in — no redundant login step.
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password())
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            redirectAttrs.addFlashAttribute("successMessage",
                    "Welcome to StockMate, " + saved.username() + "!");
            return "redirect:/products";

        } catch (IllegalArgumentException ex) {
            // Duplicate username or email — show inline form error
            log.warn("Registration rejected: {}", ex.getMessage());
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        }
    }

    // ══════════════════════════════════════════════════════════
    //  GET /auth/login — show the login form
    // ══════════════════════════════════════════════════════════
    /**
     * Renders the login form.
     *
     * Spring Security's formLogin() filter handles the actual POST /auth/login.
     * This controller method only needs to show the page and pass two optional
     * flags to Thymeleaf:
     *
     * ?error=true → bad credentials (Spring Security sets this on failure)
     * ?logout=true → user just logged out (Spring Security sets this after
     * logout)
     *
     * If the user is already authenticated, redirect them to /products
     * directly.
     */
    @GetMapping("/login")
    public String showLoginForm(
            Authentication authentication,
            Model model,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout) {

        // Fix: use instanceof check to exclude AnonymousAuthenticationToken.
        // authentication.isAuthenticated() returns true for anonymous users in
        // Spring Security 6, which would wrongly redirect them away from the
        // login page. Only a real, named principal should be redirected.
        if (authentication instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                && authentication.isAuthenticated()) {
            return "redirect:/products";
        }

        if (error != null) {
            model.addAttribute("errorMessage",
                    "Invalid username or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage",
                    "You have been logged out successfully.");
        }

        if (!model.containsAttribute("loginRequest")) {
            model.addAttribute("loginRequest", new LoginRequest("", ""));
        }

        return "auth/login";   // → templates/auth/login.html
    }
}
