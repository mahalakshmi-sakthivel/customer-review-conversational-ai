package com.customerreview.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID tenantId,
    UUID userId,
    String eventType,
    String outcome,
    String traceId,
    String ipAddress,
    String detail,
    Instant createdAt
) {}
