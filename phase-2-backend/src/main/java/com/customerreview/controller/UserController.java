package com.customerreview.controller;

import com.customerreview.dto.AppUserCreateRequest;
import com.customerreview.dto.AppUserResponse;
import com.customerreview.dto.PageResponse;
import com.customerreview.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<AppUserResponse> createUser(@Valid @RequestBody AppUserCreateRequest request) {
        AppUserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppUserResponse> getUserById(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(tenantId, id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<AppUserResponse>> getUsersByTenant(
            @RequestHeader(value = "X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AppUserResponse> userPage = userService.getUsersByTenant(tenantId, PageRequest.of(page, size));
        return ResponseEntity.ok(new PageResponse<>(
                userPage.getContent(),
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isLast()
        ));
    }
}
