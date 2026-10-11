
package com.customerreview.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        UUID tenantId,
        String email,
        String role
) {}
