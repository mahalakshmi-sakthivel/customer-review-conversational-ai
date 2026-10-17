package com.reviewai.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Login lookup. Takes a row lock so concurrent wrong-password attempts cannot
     * race past the failed-login counter. Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmailForLogin(@Param("email") String email);

    /** The only approved way to load a tenant-owned user: always scoped by tenant. */
    Optional<AppUser> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByRole(Role role);
}
