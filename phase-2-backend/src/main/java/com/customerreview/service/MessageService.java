package com.customerreview.service;

import com.customerreview.dto.MessageCreateRequest;
import com.customerreview.dto.MessageResponse;
import com.customerreview.entity.Conversation;
import com.customerreview.entity.Message;
import com.customerreview.exception.ConflictException;
import com.customerreview.exception.ResourceNotFoundException;
import com.customerreview.repository.ConversationRepository;
import com.customerreview.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public MessageService(MessageRepository messageRepository, ConversationRepository conversationRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public MessageResponse createMessage(UUID tenantId, MessageCreateRequest request) {
        // Enforce conversation belongs to this tenant
        Conversation conversation = conversationRepository.findByTenantIdAndId(tenantId, request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found in this tenant: " + request.conversationId()));

        // Enforce external message ID deduplication if provided
        if (request.externalMessageId() != null && !request.externalMessageId().isBlank()) {
            if (messageRepository.existsByTenantIdAndExternalMessageId(tenantId, request.externalMessageId().trim())) {
                throw new ConflictException("Message with external ID '" + request.externalMessageId() + "' already processed for this tenant");
            }
        }

        Message message = new Message();
        message.setTenantId(tenantId);
        message.setConversationId(request.conversationId());
        message.setDirection(request.direction());
        message.setContent(request.content());
        message.setExternalMessageId(request.externalMessageId());

        Message saved = messageRepository.save(message);

        // Increment conversation turn count
        conversation.setTurnCount(conversation.getTurnCount() + 1);
        conversationRepository.save(conversation);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(UUID tenantId, UUID conversationId, Pageable pageable) {
        // Validate conversation belongs to tenant
        conversationRepository.findByTenantIdAndId(tenantId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found in this tenant: " + conversationId));

        return messageRepository.findByTenantIdAndConversationIdOrderByCreatedAtDesc(tenantId, conversationId, pageable)
                .map(this::mapToResponse);
    }

    public MessageResponse mapToResponse(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getTenantId(),
                m.getConversationId(),
                m.getDirection(),
                m.getContent(),
                m.getExternalMessageId(),
                m.getCreatedAt()
        );
    }
}
