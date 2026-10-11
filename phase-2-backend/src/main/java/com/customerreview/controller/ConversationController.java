package com.customerreview.controller;

import com.customerreview.dto.ConversationCreateRequest;
import com.customerreview.dto.ConversationResponse;
import com.customerreview.dto.PageResponse;
import com.customerreview.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody ConversationCreateRequest request) {
        ConversationResponse response = conversationService.createConversation(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversationById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(conversationService.getConversationById(tenantId, id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ConversationResponse>> getConversations(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ConversationResponse> conversationPage = conversationService.getConversations(tenantId, status, PageRequest.of(page, size));
        return ResponseEntity.ok(new PageResponse<>(
                conversationPage.getContent(),
                conversationPage.getNumber(),
                conversationPage.getSize(),
                conversationPage.getTotalElements(),
                conversationPage.getTotalPages(),
                conversationPage.isLast()
        ));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ConversationResponse> updateStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(conversationService.updateStatus(tenantId, id, status));
    }
}
