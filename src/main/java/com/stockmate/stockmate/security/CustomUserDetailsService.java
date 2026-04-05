package com.stockmate.stockmate.security;


import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CustomUserDetailsService — Spring Security's entry point for loading a
 * user by username during the login process.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  FLOW DURING LOGIN                                              │
 * │                                                                 │
 * │  1. User submits username + password → POST /auth/login         │
 * │  2. Spring Security calls loadUserByUsername(username)          │
 * │  3. We fetch User entity from DB (roles are EAGER-loaded)       │
 * │  4. We wrap it in CustomUserDetails and return it               │
 * │  5. Spring Security calls:                                      │
 * │       PasswordEncoder.matches(submittedPlaintext, bcryptHash)  │
 * │  6. On success → CustomUserDetails stored in SecurityContext    │
 * │     for the duration of the HTTP session                        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * WHY NOT UserService.findByUsername()?
 * ──────────────────────────────────────
 * UserService.findByUsername() returns a UserResponse DTO — we need
 * the raw User ENTITY to wrap in CustomUserDetails.
 * Going directly to UserRepository here keeps the dependency simple:
 *   security → repository  ✅
 * and avoids the risk of a circular Spring bean dependency:
 *   security → service → security  ❌
 *
 * EXCEPTION CONTRACT
 * ──────────────────
 * Spring Security expects UsernameNotFoundException specifically.
 * GlobalExceptionHandler does NOT catch this — Spring Security handles it
 * internally by redirecting to /auth/login?error=true.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // ── Constructor injection ─────────────────────────────────

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ── UserDetailsService contract ───────────────────────────

    /**
     * loadUserByUsername
     *
     * Inputs     : username — the string typed in the login form field
     * Validation : user must exist (throws UsernameNotFoundException if not)
     * Repos      : UserRepository.findByUsername()
     *              (User.roles is EAGER — loaded in the same Hibernate query)
     * Business   : pure lookup — no mutation
     * Output     : CustomUserDetails (implements UserDetails)
     *
     * @throws UsernameNotFoundException caught by Spring Security's login
     *         failure handler → redirects to /auth/login?error=true
     */
    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        log.debug("Spring Security: loading user by username='{}'", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    // WARN level — repeated failures may indicate brute-force.
                    log.warn("Login attempt for unknown username: '{}'", username);
                    return new UsernameNotFoundException(
                            "No account found for username: " + username);
                });

        log.debug("User '{}' loaded — enabled={}, roles={}",
                username, user.isEnabled(), user.getRoles());

        return new CustomUserDetails(user);
    }
}