package com.reviewai.tenant;

import com.reviewai.common.ConflictException;
import com.reviewai.common.PageResponse;
import com.reviewai.common.ResourceNotFoundException;
import com.reviewai.tenant.dto.CreateTenantRequest;
import com.reviewai.tenant.dto.TenantResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@Transactional
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantResponse createTenant(CreateTenantRequest request) {
        String trimmedName = request.name().trim();
        if (tenantRepository.existsByName(trimmedName)) {
            throw new ConflictException("Tenant with name '" + trimmedName + "' already exists");
        }

        String evolutionInstance = null;
        if (StringUtils.hasText(request.evolutionInstanceName())) {
            evolutionInstance = request.evolutionInstanceName().trim();
            if (tenantRepository.existsByEvolutionInstanceName(evolutionInstance)) {
                throw new ConflictException("Tenant with Evolution API instance '" + evolutionInstance + "' already exists");
            }
        }

        Tenant tenant = new Tenant(
            UUID.randomUUID(),
            trimmedName,
            TenantStatus.ACTIVE,
            evolutionInstance
        );

        Tenant saved = tenantRepository.save(tenant);
        log.info("Created tenant '{}' with ID: {}", saved.getName(), saved.getId());
        return TenantResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantById(UUID id) {
        return tenantRepository.findById(id)
            .map(TenantResponse::fromEntity)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> getTenants(Pageable pageable) {
        Page<Tenant> page = tenantRepository.findAll(pageable);
        return PageResponse.of(page, TenantResponse::fromEntity);
    }

    public TenantResponse updateStatus(UUID id, TenantStatus newStatus) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + id));
        tenant.setStatus(newStatus);
        Tenant saved = tenantRepository.save(tenant);
        log.info("Updated status for tenant ID: {} to {}", id, newStatus);
        return TenantResponse.fromEntity(saved);
    }

    public void deleteTenant(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + id));
        tenant.setStatus(TenantStatus.DELETED);
        tenantRepository.save(tenant);
        log.info("Soft-deleted tenant ID: {}", id);
    }
}
