package com.customerreview.service;

import com.customerreview.dto.TenantCreateRequest;
import com.customerreview.dto.TenantResponse;
import com.customerreview.entity.Tenant;
import com.customerreview.exception.ConflictException;
import com.customerreview.exception.ResourceNotFoundException;
import com.customerreview.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    public TenantService(TenantRepository tenantRepository, AuditLogService auditLogService) {
        this.tenantRepository = tenantRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public TenantResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsByName(request.name())) {
            throw new ConflictException("Tenant with name '" + request.name() + "' already exists");
        }
        if (request.evolutionInstanceName() != null && !request.evolutionInstanceName().isBlank()) {
            if (tenantRepository.existsByEvolutionInstanceName(request.evolutionInstanceName())) {
                throw new ConflictException("Evolution instance '" + request.evolutionInstanceName() + "' already assigned");
            }
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.name().trim());
        tenant.setStatus(request.status() != null ? request.status() : "ACTIVE");
        tenant.setEvolutionInstanceName(request.evolutionInstanceName());

        Tenant saved = tenantRepository.save(tenant);
        auditLogService.logEvent(saved.getId(), null, "TENANT_CREATED", "SUCCESS", "Tenant created: " + saved.getName());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantById(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + id));
        return mapToResponse(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TenantResponse mapToResponse(Tenant t) {
        return new TenantResponse(
                t.getId(),
                t.getName(),
                t.getStatus(),
                t.getEvolutionInstanceName(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
