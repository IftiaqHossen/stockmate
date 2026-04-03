package com.stockmate.stockmate.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * SecurityConfig — Spring Security 6.5+ configuration for StockMate.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  TWO-LAYER SECURITY MODEL  (CLAUDE.md + PRD FR-AUTH-06/08/09)          │
 * │                                                                         │
 * │  LAYER 1 — URL level (this class)                                       │
 * │    Controls which HTTP paths are reachable by which roles.              │
 * │    e.g. /admin/** → ADMIN only; /orders/my → BUYER only                │
 * │                                                                         │
 * │  LAYER 2 — Method level (@PreAuthorize in ServiceImpl classes)          │
 * │    Controls business-rule ownership inside a role.                      │
 * │    e.g. SELLER can only edit THEIR OWN product (URL-level can't do this)│
 * │                                                                         │
 * │  BOTH layers are required — layer 1 alone is insufficient per PRD.     │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * MIGRATION NOTE — Spring Security 6.5+
 * ──────────────────────────────────────
 *   AntPathRequestMatcher is DEPRECATED and marked for removal.
 *   Replacement: PathPatternRequestMatcher
 *   Package: org.springframework.security.web.servlet.util.matcher
 *
 *   BEFORE (deprecated):
 *     import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
 *     .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout", "POST"))
 *
 *   AFTER (6.5+):
 *     import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
 *     .logoutRequestMatcher(
 *         PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/logout")
 *     )
 *
 *   PathPatternRequestMatcher uses Spring MVC's PathPatternParser — faster,
 *   stricter, and aligned with how @RequestMapping resolves paths.
 *
 * BEANS PRODUCED
 * ──────────────
 *   PasswordEncoder           BCrypt(12) — injected into UserServiceImpl
 *   DaoAuthenticationProvider links CustomUserDetailsService + PasswordEncoder
 *   AuthenticationManager     needed by AuthController for programmatic login
 *   SecurityFilterChain       URL rules, form-login, logout, session, 403 page
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity           // activates @PreAuthorize in all @Service classes
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    // ══════════════════════════════════════════════════════════════════
    //  BEAN 1 ─ PasswordEncoder
    //
    //  BCryptPasswordEncoder with strength=12 (2^12 = 4096 work rounds).
    //  Default strength is 10; 12 gives better brute-force resistance
    //  without making login noticeably slow (~300 ms on modern hardware).
    //
    //  WHERE IT IS USED
    //    UserServiceImpl.register()  → passwordEncoder.encode(plaintext)
    //    DaoAuthenticationProvider  → passwordEncoder.matches(plain, hash)
    // ══════════════════════════════════════════════════════════════════
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ══════════════════════════════════════════════════════════════════
    //  BEAN 2 ─ DaoAuthenticationProvider
    //
    //  Connects two things Spring Security needs to verify a login:
    //    1. WHO loads the user?   → CustomUserDetailsService (DB lookup)
    //    2. HOW to check password → BCrypt PasswordEncoder
    //
    //  Spring Security calls this provider automatically during form-login
    //  processing (the POST /auth/login request).
    // ══════════════════════════════════════════════════════════════════
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // ══════════════════════════════════════════════════════════════════
    //  BEAN 3 ─ AuthenticationManager
    //
    //  Exposed so AuthController can call:
    //    authManager.authenticate(new UsernamePasswordAuthenticationToken(...))
    //  for programmatic login (e.g. auto-login after registration).
    // ══════════════════════════════════════════════════════════════════
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ══════════════════════════════════════════════════════════════════
    //  BEAN 4 ─ SecurityFilterChain  (LAYER 1: URL-based access rules)
    //
    //  ORDERING MATTERS: matchers are evaluated top-to-bottom; the first
    //  match wins. More specific paths must appear before broad wildcards.
    // ══════════════════════════════════════════════════════════════════
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Wire our custom DaoAuthenticationProvider
                .authenticationProvider(authenticationProvider())

                // ── Layer 1: URL access rules ─────────────────────────────────
                .authorizeHttpRequests(auth -> auth

                        // ── 1. PUBLIC — no login required ─────────────────────────
                        // Login + register must be reachable before authentication.
                        // Static assets must be public or the login page will be unstyled.
                        .requestMatchers(
                                "/auth/login",
                                "/auth/register",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/error"              // Spring's built-in /error endpoint
                        ).permitAll()

                        // ── 2. ADMIN-only ─────────────────────────────────────────
                        // /admin/**, /admin/users, /admin/users/{id}/role, etc.
                        // A BUYER or SELLER hitting /admin/** receives HTTP 403.
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // ── 3. SELLER-only ────────────────────────────────────────
                        // Product create/edit form pages and seller dashboard.
                        // ADMIN can also edit products — that ownership distinction
                        // is enforced by @PreAuthorize in ProductServiceImpl (layer 2).
                        .requestMatchers(
                                "/products/new",
                                "/products/*/edit",
                                "/dashboard/seller",
                                "/orders/seller"
                        ).hasRole("SELLER")

                        // ── 4. BUYER-only ─────────────────────────────────────────
                        .requestMatchers(
                                "/dashboard/buyer",
                                "/orders/my"
                        ).hasRole("BUYER")

                        // ── 5. Any authenticated user ─────────────────────────────
                        // Product catalogue, product detail, category list, etc.
                        // Unauthenticated requests are redirected to /auth/login.
                        .anyRequest().authenticated()
                )

                // ── Form login ────────────────────────────────────────────────
                .formLogin(form -> form
                        .loginPage("/auth/login")              // GET  → show Thymeleaf login page
                        .loginProcessingUrl("/auth/login")     // POST → Spring Security authenticates
                        .defaultSuccessUrl("/products", true)  // success → product catalogue
                        .failureUrl("/auth/login?error=true")  // failure → show error on login page
                        .permitAll()
                )

                // ── Logout ────────────────────────────────────────────────────
                //
                // Spring Security 6.5+ replaces deprecated AntPathRequestMatcher
                // with PathPatternRequestMatcher for all request matching.
                //
                // PathPatternRequestMatcher.withDefaults() creates a factory that
                // uses Spring MVC's PathPatternParser (the same engine as @RequestMapping).
                // .matcher(HttpMethod.POST, "/auth/logout") produces a matcher that
                // only triggers on POST requests to exactly /auth/logout — correct
                // behaviour to avoid CSRF-exploitable GET logout.
                //
                // Thymeleaf template requirement:
                //   <form th:action="@{/auth/logout}" method="post">
                //     <button type="submit">Logout</button>
                //   </form>
                //   Thymeleaf auto-injects the hidden _csrf field via th:action.
                .logout(logout -> logout
                        .logoutRequestMatcher(
                                PathPatternRequestMatcher.withDefaults()
                                        .matcher(HttpMethod.POST, "/auth/logout")
                        )
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .invalidateHttpSession(true)           // destroy the server-side session
                        .deleteCookies("JSESSIONID")           // remove the session cookie
                        .clearAuthentication(true)
                        .permitAll()
                )

                // ── Session management ────────────────────────────────────────
                // migrateSession() assigns a new session ID after successful login
                // → prevents session-fixation attacks.
                // maximumSessions(1) blocks a second concurrent login on the same account.
                .sessionManagement(session -> session
                        .sessionFixation().migrateSession()
                        .maximumSessions(1)
                )

                // ── 403 Forbidden page ────────────────────────────────────────
                // URL-level denials redirect to our Thymeleaf 403 template.
                // Method-level AccessDeniedException (from @PreAuthorize or service
                // layer programmatic throws) is caught by GlobalExceptionHandler.
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/error/403")
                );

        // CSRF left at Spring Security default = ENABLED.
        // Thymeleaf's th:action automatically injects the _csrf hidden field.
        // Never disable CSRF in a session-based Thymeleaf application.

        return http.build();
    }
}