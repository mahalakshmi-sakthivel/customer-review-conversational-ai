package com.reviewai.tenant.dto;

import com.reviewai.tenant.Tenant;
import com.reviewai.tenant.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
    UUID id,
    String name,
    TenantStatus status,
    String evolutionInstanceName,
    Instant createdAt,
    Instant updatedAt
) {
    public static TenantResponse fromEntity(Tenant tenant) {
        return new TenantResponse(
            tenant.getId(),
            tenant.getName(),
            tenant.getStatus(),
            tenant.getEvolutionInstanceName(),
            tenant.getCreatedAt(),
            tenant.getUpdatedAt()
        );
    }
}
