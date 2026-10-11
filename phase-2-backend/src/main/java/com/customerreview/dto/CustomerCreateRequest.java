package com.customerreview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
    @Size(max = 150, message = "Name must not exceed 150 characters")
    String name,

    @NotBlank(message = "Phone number is required")
    String phone,

    String email,

    Boolean optedOut,

    String purchaseInfo
) {}
