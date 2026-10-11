package com.customerreview.repository;

import com.customerreview.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    
    Optional<Conversation> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT c FROM Conversation c WHERE c.tenantId = :tenantId AND c.customerId = :customerId AND c.status = 'ACTIVE'")
    Optional<Conversation> findActiveConversation(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId);

    Page<Conversation> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Conversation> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<Conversation> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId, Pageable pageable);
}
