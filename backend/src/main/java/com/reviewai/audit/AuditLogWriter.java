package com.reviewai.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one audit row in its own transaction so the entry survives even when the
 * calling request transaction rolls back (for example a failed login).
 */
@Component
class AuditLogWriter {

    private final AuditLogRepository repository;

    AuditLogWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        repository.saveAndFlush(entry);
    }
}
