package com.customerreview.repository;

import com.customerreview.entity.Lead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID> {
    
    Optional<Lead> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<Lead> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Lead> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<Lead> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId, Pageable pageable);
}
