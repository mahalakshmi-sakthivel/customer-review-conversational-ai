package com.customerreview.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    UUID tenantId,
    UUID customerId,
    String status,
    int turnCount,
    Instant startedAt,
    Instant endedAt,
    Instant createdAt,
    Instant updatedAt
) {}
