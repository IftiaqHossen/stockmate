package com.stockmate.stockmate.dto.request;

import jakarta.validation.constraints.NotBlank;

/** LoginRequest — payload for POST /auth/login. */
public record LoginRequest(

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}

