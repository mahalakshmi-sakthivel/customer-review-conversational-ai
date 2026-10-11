package com.customerreview.dto;

import java.time.Instant;
import java.util.UUID;

public record LeadResponse(
    UUID id,
    UUID tenantId,
    UUID customerId,
    String status,
    Instant eligibleAt,
    Instant createdAt,
    Instant updatedAt
) {}
