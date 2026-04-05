package com.stockmate.stockmate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RegisterRequest — payload for POST /auth/register.
 *
 * role must be "ROLE_BUYER" or "ROLE_SELLER".
 * "ROLE_ADMIN" is blocked at the service layer (UserServiceImpl.register).
 */
public record RegisterRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "ROLE_BUYER|ROLE_SELLER",
                message = "Role must be ROLE_BUYER or ROLE_SELLER")
        String role
) {}


