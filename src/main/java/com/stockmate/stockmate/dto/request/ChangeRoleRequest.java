package com.stockmate.stockmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * ChangeRoleRequest — form model for admin role update submissions.
 */
public record ChangeRoleRequest(
        @NotBlank(message = "Role is required")
        @Pattern(regexp = "ROLE_BUYER|ROLE_SELLER|ROLE_ADMIN",
                message = "Role must be ROLE_BUYER, ROLE_SELLER, or ROLE_ADMIN")
        String newRole
        ) {

}
