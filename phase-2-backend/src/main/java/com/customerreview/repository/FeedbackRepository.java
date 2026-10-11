package com.customerreview.repository;

import com.customerreview.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    
    Optional<Feedback> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Feedback> findByTenantIdAndConversationId(UUID tenantId, UUID conversationId);

    Page<Feedback> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Feedback> findByTenantIdAndSentiment(UUID tenantId, String sentiment, Pageable pageable);

    Page<Feedback> findByTenantIdAndRating(UUID tenantId, short rating, Pageable pageable);
}
