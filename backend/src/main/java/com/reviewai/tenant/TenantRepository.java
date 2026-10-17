package com.reviewai.tenant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByName(String name);

    boolean existsByEvolutionInstanceName(String evolutionInstanceName);

    Optional<Tenant> findByName(String name);

    Page<Tenant> findAllByStatusNot(TenantStatus status, Pageable pageable);
}
