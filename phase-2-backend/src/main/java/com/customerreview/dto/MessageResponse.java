package com.customerreview.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID tenantId,
    UUID conversationId,
    String direction,
    String content,
    String externalMessageId,
    Instant createdAt
) {}
