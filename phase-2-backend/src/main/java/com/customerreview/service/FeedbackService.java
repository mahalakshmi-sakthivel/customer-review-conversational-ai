package com.customerreview.service;

import com.customerreview.dto.FeedbackCreateRequest;
import com.customerreview.dto.FeedbackResponse;
import com.customerreview.entity.Feedback;
import com.customerreview.exception.ConflictException;
import com.customerreview.exception.ResourceNotFoundException;
import com.customerreview.repository.ConversationRepository;
import com.customerreview.repository.CustomerRepository;
import com.customerreview.repository.FeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ConversationRepository conversationRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           ConversationRepository conversationRepository,
                           CustomerRepository customerRepository,
                           AuditLogService auditLogService) {
        this.feedbackRepository = feedbackRepository;
        this.conversationRepository = conversationRepository;
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public FeedbackResponse createFeedback(UUID tenantId, FeedbackCreateRequest request) {
        // Enforce conversation belongs to this tenant
        conversationRepository.findByTenantIdAndId(tenantId, request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found in this tenant: " + request.conversationId()));

        // Enforce customer belongs to this tenant
        customerRepository.findByTenantIdAndId(tenantId, request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found in this tenant: " + request.customerId()));

        // Enforce 1 feedback per conversation
        if (feedbackRepository.findByTenantIdAndConversationId(tenantId, request.conversationId()).isPresent()) {
            throw new ConflictException("Feedback has already been recorded for this conversation");
        }

        Feedback feedback = new Feedback();
        feedback.setTenantId(tenantId);
        feedback.setConversationId(request.conversationId());
        feedback.setCustomerId(request.customerId());
        feedback.setRating(request.rating());
        feedback.setProblem(request.problem());
        feedback.setSuggestion(request.suggestion());
        feedback.setExpectation(request.expectation());
        feedback.setSentiment(request.sentiment());

        Feedback saved = feedbackRepository.save(feedback);
        auditLogService.logEvent(tenantId, null, "FEEDBACK_SUBMITTED", "SUCCESS", "Rating: " + request.rating() + ", Sentiment: " + request.sentiment());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public FeedbackResponse getFeedbackById(UUID tenantId, UUID feedbackId) {
        Feedback feedback = feedbackRepository.findByTenantIdAndId(tenantId, feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with ID: " + feedbackId));
        return mapToResponse(feedback);
    }

    @Transactional(readOnly = true)
    public Page<FeedbackResponse> getFeedbackList(UUID tenantId, Short rating, String sentiment, Pageable pageable) {
        if (rating != null) {
            return feedbackRepository.findByTenantIdAndRating(tenantId, rating, pageable)
                    .map(this::mapToResponse);
        }
        if (sentiment != null && !sentiment.isBlank()) {
            return feedbackRepository.findByTenantIdAndSentiment(tenantId, sentiment, pageable)
                    .map(this::mapToResponse);
        }
        return feedbackRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    public FeedbackResponse mapToResponse(Feedback f) {
        return new FeedbackResponse(
                f.getId(),
                f.getTenantId(),
                f.getConversationId(),
                f.getCustomerId(),
                f.getRating(),
                f.getProblem(),
                f.getSuggestion(),
                f.getExpectation(),
                f.getSentiment(),
                f.getCreatedAt()
        );
    }
}
