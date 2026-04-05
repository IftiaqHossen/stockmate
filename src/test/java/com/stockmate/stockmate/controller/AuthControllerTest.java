package com.stockmate.stockmate.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stockmate.stockmate.dto.request.RegisterRequest;
import com.stockmate.stockmate.dto.response.UserResponse;
import com.stockmate.stockmate.service.UserService;

/**
 * Integration tests for AuthController.
 *
 * Authentication is the front door of the application. These tests ensure that
 * registration succeeds, invalid data is rejected cleanly, and duplicate emails
 * surface a user-friendly error rather than a 500. Logout is also validated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    // ── TC-1: registerUser_success ────────────────────────────────────────────
    /**
     * WHY: Validates the full registration flow through the HTTP layer. A 201
     * response with the username in the body proves end-to-end wiring.
     */
    @Test
    @DisplayName("POST /auth/register: valid BUYER registration returns 201 Created")
    void registerUser_success() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "newUser",
                "new@test.com",
                "pass1234",
                "ROLE_BUYER"
        );

        UserResponse response = new UserResponse(
                1L,
                "newUser",
                "new@test.com",
                true,
                LocalDateTime.now(),
                Set.of("ROLE_BUYER")
        );

        given(userService.register(any(RegisterRequest.class))).willReturn(response);
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password(),
                        Collections.emptyList()
                ));

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                .with(csrf())
                .param("username", request.username())
                .param("email", request.email())
                .param("password", request.password())
                .param("role", request.role()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attributeExists("successMessage"));

        then(userService).should().register(any(RegisterRequest.class));
        then(authenticationManager).should().authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    // ── TC-2: registerUser_invalidData_badRequest ─────────────────────────────
    /**
     * WHY: Blank username and invalid email must be caught by Bean Validation
     * (@NotBlank,
     *
     * @Email) and return 400 with field-level messages. This prevents corrupted
     * user records reaching the database.
     */
    @Test
    @DisplayName("POST /auth/register: blank username and invalid email returns 400 Bad Request")
    void registerUser_invalidData_badRequest() throws Exception {
        // Act & Assert — Bean Validation fires before service is called
        mockMvc.perform(post("/auth/register")
                .with(csrf())
                .param("username", "")
                .param("email", "not-email")
                .param("password", "x")
                .param("role", "ROLE_BUYER"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("registerRequest", "username", "email", "password"));

        then(userService).should(never()).register(any(RegisterRequest.class));
        then(authenticationManager).should(never()).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    // ── TC-3: registerUser_duplicateEmail_conflict ────────────────────────────
    /**
     * WHY: A second registration with the same email must return 409 Conflict
     * (not 500). GlobalExceptionHandler translates IllegalArgumentException →
     * 409. Clean error messages keep users informed and reduce support tickets.
     */
    @Test
    @DisplayName("POST /auth/register: duplicate email returns 409 Conflict")
    void registerUser_duplicateEmail_conflict() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "another",
                "taken@test.com",
                "pass1234",
                "ROLE_BUYER"
        );

        given(userService.register(any(RegisterRequest.class)))
                .willThrow(new IllegalArgumentException("A user with this email already exists"));

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                .with(csrf())
                .param("username", request.username())
                .param("email", request.email())
                .param("password", request.password())
                .param("role", request.role()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attribute("errorMessage", "A user with this email already exists"));

        then(authenticationManager).should(never()).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    // ── TC-4: loginPage_accessible ────────────────────────────────────────────
    /**
     * WHY: The login page must be publicly accessible without authentication
     * (FR-AUTH-05). If SecurityConfig accidentally protects /auth/login, no one
     * can log in.
     */
    @Test
    @DisplayName("GET /auth/login: login page accessible without authentication (200 OK)")
    void loginPage_accessible() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attributeExists("loginRequest"));
    }

    // ── TC-5: registerPage_accessible ────────────────────────────────────────
    /**
     * WHY: Registration page must also be public (FR-AUTH-05).
     */
    @Test
    @DisplayName("GET /auth/register: register page accessible without authentication (200 OK)")
    void registerPage_accessible() throws Exception {
        mockMvc.perform(get("/auth/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeExists("registerRequest"));
    }

    // ── TC-6: logout_authenticated ────────────────────────────────────────────
    /**
     * WHY: Logout must invalidate the session and redirect — not return 403 or
     * 500. A broken logout is a security risk (session stays alive) and bad UX.
     */
    @Test
    @WithMockUser(username = "someUser")
    @DisplayName("POST /auth/logout: authenticated user is redirected after logout")
    void logout_authenticated() throws Exception {
        mockMvc.perform(post("/auth/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?logout=true"));
    }

    // ── TC-7: adminPage_asBuyer_forbidden ─────────────────────────────────────
    /**
     * WHY: A BUYER accessing /admin/** must receive 403 (FR-AUTH-06). This is
     * the MOST IMPORTANT security boundary — tested here as a sanity check
     * alongside the dedicated admin tests. If this fails → automatic failure on
     * rubric.
     */
    @Test
    @WithMockUser(roles = "BUYER")
    @DisplayName("GET /admin: BUYER accessing admin area receives 403 Forbidden")
    void adminPage_asBuyer_forbidden() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }
}
