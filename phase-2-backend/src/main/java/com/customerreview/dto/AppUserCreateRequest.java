package com.customerreview.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AppUserCreateRequest(
    UUID tenantId,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    String password,

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "SUPER_ADMIN|TENANT_ADMIN|OPERATIONS_USER", message = "Role must be SUPER_ADMIN, TENANT_ADMIN, or OPERATIONS_USER")
    String role
) {}
