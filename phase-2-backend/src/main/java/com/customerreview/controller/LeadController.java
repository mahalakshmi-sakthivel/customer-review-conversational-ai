package com.customerreview.controller;

import com.customerreview.dto.LeadCreateRequest;
import com.customerreview.dto.LeadResponse;
import com.customerreview.dto.PageResponse;
import com.customerreview.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    public ResponseEntity<LeadResponse> createLead(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody LeadCreateRequest request) {
        LeadResponse response = leadService.createLead(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> getLeadById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(leadService.getLeadById(tenantId, id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<LeadResponse>> getLeads(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<LeadResponse> leadPage = leadService.getLeads(tenantId, status, PageRequest.of(page, size));
        return ResponseEntity.ok(new PageResponse<>(
                leadPage.getContent(),
                leadPage.getNumber(),
                leadPage.getSize(),
                leadPage.getTotalElements(),
                leadPage.getTotalPages(),
                leadPage.isLast()
        ));
    }
}
