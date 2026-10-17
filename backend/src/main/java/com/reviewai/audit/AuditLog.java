package com.reviewai.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Append-only row (Phase 1 section 3.8). All columns are non-updatable. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 10)
    private AuditOutcome outcome;

    @Column(name = "trace_id", updatable = false, length = 64)
    private String traceId;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", updatable = false)
    private Map<String, Object> detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(AuditEventType eventType, AuditOutcome outcome, UUID tenantId, UUID userId,
                    String traceId, String ipAddress, Map<String, Object> detail) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.outcome = outcome;
        this.tenantId = tenantId;
        this.userId = userId;
        this.traceId = traceId;
        this.ipAddress = ipAddress;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public AuditEventType getEventType() { return eventType; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getTraceId() { return traceId; }
    public String getIpAddress() { return ipAddress; }
    public Map<String, Object> getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
