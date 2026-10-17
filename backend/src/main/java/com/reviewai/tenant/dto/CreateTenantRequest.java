package com.reviewai.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
    @NotBlank(message = "Tenant name is required")
    @Size(max = 150, message = "Tenant name cannot exceed 150 characters")
    String name,

    @Size(max = 100, message = "Evolution instance name cannot exceed 100 characters")
    String evolutionInstanceName
) {}
