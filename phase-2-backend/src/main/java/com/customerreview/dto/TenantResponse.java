package com.customerreview.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
    UUID id,
    String name,
    String status,
    String evolutionInstanceName,
    Instant createdAt,
    Instant updatedAt
) {}
