package com.customerreview.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
    UUID id,
    UUID tenantId,
    String name,
    String phone, // masked or decrypted for authorized viewer
    String phoneHash,
    String email,
    boolean optedOut,
    String purchaseInfo,
    Instant createdAt,
    Instant updatedAt
) {}
