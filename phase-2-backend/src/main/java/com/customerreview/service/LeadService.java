package com.customerreview.service;

import com.customerreview.dto.LeadCreateRequest;
import com.customerreview.dto.LeadResponse;
import com.customerreview.entity.Lead;
import com.customerreview.exception.ResourceNotFoundException;
import com.customerreview.repository.CustomerRepository;
import com.customerreview.repository.LeadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    public LeadService(LeadRepository leadRepository, CustomerRepository customerRepository, AuditLogService auditLogService) {
        this.leadRepository = leadRepository;
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LeadResponse createLead(UUID tenantId, LeadCreateRequest request) {
        // Ensure customer exists and belongs to this tenant
        customerRepository.findByTenantIdAndId(tenantId, request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found in this tenant: " + request.customerId()));

        Lead lead = new Lead();
        lead.setTenantId(tenantId);
        lead.setCustomerId(request.customerId());
        lead.setStatus(request.status() != null ? request.status() : "NEW");
        lead.setEligibleAt(request.eligibleAt());

        Lead saved = leadRepository.save(lead);
        auditLogService.logEvent(tenantId, null, "LEAD_CREATED", "SUCCESS", "Lead created for customer: " + request.customerId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public LeadResponse getLeadById(UUID tenantId, UUID leadId) {
        Lead lead = leadRepository.findByTenantIdAndId(tenantId, leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found with ID: " + leadId + " in tenant: " + tenantId));
        return mapToResponse(lead);
    }

    @Transactional(readOnly = true)
    public Page<LeadResponse> getLeads(UUID tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return leadRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                    .map(this::mapToResponse);
        }
        return leadRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    public LeadResponse mapToResponse(Lead l) {
        return new LeadResponse(
                l.getId(),
                l.getTenantId(),
                l.getCustomerId(),
                l.getStatus(),
                l.getEligibleAt(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }
}
