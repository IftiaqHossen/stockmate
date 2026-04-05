package com.stockmate.stockmate.dto.response;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * UserResponse — never includes the password field.
 * Returned by UserService and exposed through AdminController.
 */
public record UserResponse(
        Long            id,
        String          username,
        String          email,
        boolean         enabled,
        LocalDateTime   createdAt,
        Set<String>     roles       // e.g. ["ROLE_BUYER"]
) {}