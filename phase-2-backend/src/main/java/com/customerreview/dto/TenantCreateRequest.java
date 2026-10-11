package com.customerreview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantCreateRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    String name,

    @Pattern(regexp = "ACTIVE|SUSPENDED", message = "Status must be ACTIVE or SUSPENDED")
    String status,

    @Size(max = 100, message = "Evolution instance name must not exceed 100 characters")
    String evolutionInstanceName
) {}
