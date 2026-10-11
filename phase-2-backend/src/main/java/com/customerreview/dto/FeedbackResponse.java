package com.customerreview.dto;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
    UUID id,
    UUID tenantId,
    UUID conversationId,
    UUID customerId,
    short rating,
    String problem,
    String suggestion,
    String expectation,
    String sentiment,
    Instant createdAt
) {}
