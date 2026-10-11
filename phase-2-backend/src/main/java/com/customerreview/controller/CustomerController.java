package com.customerreview.controller;

import com.customerreview.dto.CustomerCreateRequest;
import com.customerreview.dto.CustomerResponse;
import com.customerreview.dto.PageResponse;
import com.customerreview.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CustomerCreateRequest request) {
        CustomerResponse response = customerService.createCustomer(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerById(tenantId, id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<CustomerResponse>> getCustomers(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CustomerResponse> customerPage = customerService.getCustomers(tenantId, PageRequest.of(page, size));
        return ResponseEntity.ok(new PageResponse<>(
                customerPage.getContent(),
                customerPage.getNumber(),
                customerPage.getSize(),
                customerPage.getTotalElements(),
                customerPage.getTotalPages(),
                customerPage.isLast()
        ));
    }

    @PatchMapping("/{id}/opt-out")
    public ResponseEntity<CustomerResponse> updateOptOut(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam boolean optedOut) {
        return ResponseEntity.ok(customerService.updateOptOut(tenantId, id, optedOut));
    }
}
