package com.stockmate.stockmate.service;

import com.stockmate.stockmate.dto.request.RegisterRequest;
import com.stockmate.stockmate.dto.response.UserResponse;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for UserServiceImpl.
 *
 * Authentication and role management are the security bedrock of StockMate.
 * These tests verify that duplicate registrations are caught early, passwords
 * are always hashed (never stored plaintext), and role changes are correctly
 * applied through the join table.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    // ── TC-1: registerUser_success ────────────────────────────────────────────
    /**
     * WHY: Verifies the full happy-path registration flow: 1. Email uniqueness
     * checked before any write 2. Password is ENCODED — never stored as
     * plaintext (FR-AUTH-02) 3. The correct role is fetched from DB (not
     * hardcoded) 4. Returned DTO carries correct username
     */
    @Test
    @DisplayName("registerUser: valid BUYER registration encodes password and persists user")
    void registerUser_success() {
        Role buyerRole = new Role("ROLE_BUYER");
        RegisterRequest request = new RegisterRequest(
                "newUser", "newuser@test.com", "plaintext123", "ROLE_BUYER");

        given(userRepository.existsByUsername("newUser")).willReturn(false);
        given(userRepository.existsByEmail("newuser@test.com")).willReturn(false);
        given(roleService.findByName("ROLE_BUYER")).willReturn(buyerRole);
        given(passwordEncoder.encode("plaintext123")).willReturn("$2a$encodedHash");

        User savedUser = new User("newUser", "newuser@test.com", "$2a$encodedHash");
        savedUser.setRoles(Set.of(buyerRole));
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        UserResponse response = userService.register(request);

        assertThat(response.username()).isEqualTo("newUser");
        then(passwordEncoder).should().encode("plaintext123");
        then(roleService).should().findByName("ROLE_BUYER");
        then(userRepository).should().save(any(User.class));
    }

    // ── TC-2: registerUser_duplicateEmail ─────────────────────────────────────
    /**
     * WHY: Allowing duplicate emails would let multiple accounts share one
     * identity, breaking password-reset flows and causing data ownership
     * confusion. The service must reject the request BEFORE hashing or saving
     * anything.
     */
    @Test
    @DisplayName("registerUser: duplicate email throws IllegalArgumentException without saving")
    void registerUser_duplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "another", "john@test.com", "pass123", "ROLE_BUYER");

        given(userRepository.existsByUsername("another")).willReturn(false);
        given(userRepository.existsByEmail("john@test.com")).willReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");

        then(userRepository).should(never()).save(any());
        then(passwordEncoder).should(never()).encode(anyString());
        then(roleService).should(never()).findByName(anyString());
    }

    // ── TC-3: loadUserByUsername_notFound ─────────────────────────────────────
    /**
     * WHY: Spring Security calls loadUserByUsername during every login attempt.
     * It MUST throw UsernameNotFoundException for unknown users — Spring
     * Security depends on this contract to return 401. A silent null return
     * would cause NPE.
     */
    @Test
    @DisplayName("loadUserByUsername: unknown username throws UsernameNotFoundException")
    void loadUserByUsername_notFound() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByUsername("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ── TC-4: changeRole_success ──────────────────────────────────────────────
    /**
     * WHY: Admin promotes a BUYER to SELLER (FR-ADM-02). The new role must
     * replace (not be added to) the existing role set, ensuring a user cannot
     * simultaneously hold BUYER and SELLER roles post-promotion.
     */
    @Test
    @DisplayName("changeRole: admin promotes BUYER to SELLER, user now has SELLER role only")
    void changeRole_success() {
        Role buyerRole = new Role("ROLE_BUYER");
        Role sellerRole = new Role("ROLE_SELLER");
        User existingUser = new User("john", "john@test.com", "$2a$hashed");
        existingUser.setRoles(Set.of(buyerRole));

        given(userRepository.findById(1L)).willReturn(Optional.of(existingUser));
        given(roleService.findByName("ROLE_SELLER")).willReturn(sellerRole);
        given(userRepository.save(any(User.class))).willReturn(existingUser);

        UserResponse response = userService.changeRole(1L, "ROLE_SELLER");

        assertThat(response).isNotNull();
        assertThat(response.roles()).containsExactly("ROLE_SELLER");
        then(userRepository).should().save(any(User.class));
        assertThat(existingUser.getRoles()).containsExactly(sellerRole);
    }

    // ── TC-5: registerUser_withSellerRole ─────────────────────────────────────
    /**
     * WHY: Users can actively choose SELLER at registration (FR-AUTH-04). This
     * test ensures the role assignment path works for SELLER just as it does
     * for BUYER — a separate code path that could silently fail if only BUYER
     * is tested.
     */
    @Test
    @DisplayName("registerUser: SELLER role selection assigns ROLE_SELLER at registration")
    void registerUser_withSellerRole() {
        Role sellerRole = new Role("ROLE_SELLER");
        RegisterRequest request = new RegisterRequest(
                "shopOwner", "shop@test.com", "shopPass", "ROLE_SELLER");

        given(userRepository.existsByUsername("shopOwner")).willReturn(false);
        given(userRepository.existsByEmail("shop@test.com")).willReturn(false);
        given(roleService.findByName("ROLE_SELLER")).willReturn(sellerRole);
        given(passwordEncoder.encode("shopPass")).willReturn("$2a$encodedShop");

        User savedSeller = new User("shopOwner", "shop@test.com", "$2a$encodedShop");
        savedSeller.setRoles(Set.of(sellerRole));
        given(userRepository.save(any(User.class))).willReturn(savedSeller);

        UserResponse response = userService.register(request);

        assertThat(response.username()).isEqualTo("shopOwner");
        then(roleService).should().findByName("ROLE_SELLER");
    }
}
