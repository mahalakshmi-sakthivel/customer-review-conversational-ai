package com.customerreview.repository;

import com.customerreview.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    
    Optional<Customer> findByTenantIdAndPhoneHash(UUID tenantId, String phoneHash);

    Optional<Customer> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<Customer> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndPhoneHash(UUID tenantId, String phoneHash);
}
