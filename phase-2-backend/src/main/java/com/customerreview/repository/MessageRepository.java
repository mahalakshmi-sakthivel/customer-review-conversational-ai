package com.customerreview.repository;

import com.customerreview.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    
    Optional<Message> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<Message> findByTenantIdAndConversationIdOrderByCreatedAtDesc(UUID tenantId, UUID conversationId, Pageable pageable);

    boolean existsByTenantIdAndExternalMessageId(UUID tenantId, String externalMessageId);
}
