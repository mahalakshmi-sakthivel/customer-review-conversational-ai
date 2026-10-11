package com.customerreview.controller;

import com.customerreview.dto.FeedbackCreateRequest;
import com.customerreview.dto.FeedbackResponse;
import com.customerreview.dto.PageResponse;
import com.customerreview.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<FeedbackResponse> createFeedback(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody FeedbackCreateRequest request) {
        FeedbackResponse response = feedbackService.createFeedback(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeedbackResponse> getFeedbackById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(feedbackService.getFeedbackById(tenantId, id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<FeedbackResponse>> getFeedbackList(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) Short rating,
            @RequestParam(required = false) String sentiment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FeedbackResponse> feedbackPage = feedbackService.getFeedbackList(tenantId, rating, sentiment, PageRequest.of(page, size));
        return ResponseEntity.ok(new PageResponse<>(
                feedbackPage.getContent(),
                feedbackPage.getNumber(),
                feedbackPage.getSize(),
                feedbackPage.getTotalElements(),
                feedbackPage.getTotalPages(),
                feedbackPage.isLast()
        ));
    }
}
