package com.customerreview.service;

import com.customerreview.dto.ConversationCreateRequest;
import com.customerreview.dto.ConversationResponse;
import com.customerreview.entity.Conversation;
import com.customerreview.exception.ConflictException;
import com.customerreview.exception.ResourceNotFoundException;
import com.customerreview.repository.ConversationRepository;
import com.customerreview.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    public ConversationService(ConversationRepository conversationRepository,
                               CustomerRepository customerRepository,
                               AuditLogService auditLogService) {
        this.conversationRepository = conversationRepository;
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ConversationResponse createConversation(UUID tenantId, ConversationCreateRequest request) {
        // Enforce customer belongs to tenant
        customerRepository.findByTenantIdAndId(tenantId, request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found in this tenant: " + request.customerId()));

        String targetStatus = request.status() != null ? request.status() : "ACTIVE";

        // Enforce one active conversation constraint
        if ("ACTIVE".equalsIgnoreCase(targetStatus)) {
            if (conversationRepository.findActiveConversation(tenantId, request.customerId()).isPresent()) {
                throw new ConflictException("Customer already has an ACTIVE conversation in this tenant. Resolve it first.");
            }
        }

        Conversation conversation = new Conversation();
        conversation.setTenantId(tenantId);
        conversation.setCustomerId(request.customerId());
        conversation.setStatus(targetStatus);
        conversation.setTurnCount(0);
        conversation.setStartedAt(Instant.now());

        Conversation saved = conversationRepository.save(conversation);
        auditLogService.logEvent(tenantId, null, "CONVERSATION_STARTED", "SUCCESS", "Conversation started ID: " + saved.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationById(UUID tenantId, UUID conversationId) {
        Conversation conversation = conversationRepository.findByTenantIdAndId(tenantId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + conversationId + " in tenant: " + tenantId));
        return mapToResponse(conversation);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getConversations(UUID tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return conversationRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                    .map(this::mapToResponse);
        }
        return conversationRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public ConversationResponse updateStatus(UUID tenantId, UUID conversationId, String newStatus) {
        Conversation conversation = conversationRepository.findByTenantIdAndId(tenantId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        
        conversation.setStatus(newStatus);
        if (!"ACTIVE".equalsIgnoreCase(newStatus) && conversation.getEndedAt() == null) {
            conversation.setEndedAt(Instant.now());
        }
        Conversation saved = conversationRepository.save(conversation);
        auditLogService.logEvent(tenantId, null, "CONVERSATION_STATUS_CHANGED", "SUCCESS", "Conversation " + conversationId + " marked as " + newStatus);
        return mapToResponse(saved);
    }

    public ConversationResponse mapToResponse(Conversation c) {
        return new ConversationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCustomerId(),
                c.getStatus(),
                c.getTurnCount(),
                c.getStartedAt(),
                c.getEndedAt(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
