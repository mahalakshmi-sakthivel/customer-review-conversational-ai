package com.customerreview.controller;

import com.customerreview.dto.MessageCreateRequest;
import com.customerreview.dto.MessageResponse;
import com.customerreview.dto.PageResponse;
import com.customerreview.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> createMessage(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody MessageCreateRequest request) {
        MessageResponse response = messageService.createMessage(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<PageResponse<MessageResponse>> getConversationMessages(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<MessageResponse> messagePage = messageService.getConversationMessages(tenantId, conversationId, PageRequest.of(page, size));
        return ResponseEntity.ok(new PageResponse<>(
                messagePage.getContent(),
                messagePage.getNumber(),
                messagePage.getSize(),
                messagePage.getTotalElements(),
                messagePage.getTotalPages(),
                messagePage.isLast()
        ));
    }
}
