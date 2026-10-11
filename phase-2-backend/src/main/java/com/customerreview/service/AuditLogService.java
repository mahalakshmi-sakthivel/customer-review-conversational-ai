package com.customerreview.service;

import com.customerreview.dto.AuditLogResponse;
import com.customerreview.entity.AuditLog;
import com.customerreview.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(UUID tenantId, UUID userId, String eventType, String outcome, String detailMessage) {
        try {
            String detailJson = detailMessage != null ? "{\"message\": \"" + detailMessage.replace("\"", "\\\"") + "\"}" : null;
            AuditLog auditLog = new AuditLog(tenantId, userId, eventType, outcome, UUID.randomUUID().toString(), "127.0.0.1", detailJson);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getLogsForTenant(UUID tenantId, Pageable pageable) {
        return auditLogRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    private AuditLogResponse mapToResponse(AuditLog a) {
        return new AuditLogResponse(
                a.getId(),
                a.getTenantId(),
                a.getUserId(),
                a.getEventType(),
                a.getOutcome(),
                a.getTraceId(),
                a.getIpAddress(),
                a.getDetail(),
                a.getCreatedAt()
        );
    }
}
