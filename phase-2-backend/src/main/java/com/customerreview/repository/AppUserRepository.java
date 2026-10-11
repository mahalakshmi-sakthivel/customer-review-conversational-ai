package com.customerreview.repository;

import com.customerreview.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    
    @Query("SELECT u FROM AppUser u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<AppUser> findByEmailLower(@Param("email") String email);

    Page<AppUser> findByTenantId(UUID tenantId, Pageable pageable);

    Page<AppUser> findByTenantIdAndRole(UUID tenantId, String role, Pageable pageable);

    @Query("SELECT COUNT(u) > 0 FROM AppUser u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailLower(@Param("email") String email);
}
