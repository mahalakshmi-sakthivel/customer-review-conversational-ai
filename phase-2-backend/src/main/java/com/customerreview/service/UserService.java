package com.customerreview.service;

import com.customerreview.dto.AppUserCreateRequest;
import com.customerreview.dto.AppUserResponse;
import com.customerreview.entity.AppUser;
import com.customerreview.exception.ConflictException;
import com.customerreview.exception.ResourceNotFoundException;
import com.customerreview.repository.AppUserRepository;
import com.customerreview.security.PasswordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final AppUserRepository userRepository;
    private final PasswordService passwordService;
    private final AuditLogService auditLogService;

    public UserService(AppUserRepository userRepository, PasswordService passwordService, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AppUserResponse createUser(AppUserCreateRequest request) {
        if (userRepository.existsByEmailLower(request.email())) {
            throw new ConflictException("User with email '" + request.email() + "' already exists");
        }

        AppUser user = new AppUser();
        user.setEmail(request.email().toLowerCase().trim());
        user.setPasswordHash(passwordService.hashPassword(request.password()));
        user.setRole(request.role());
        user.setTenantId(request.tenantId());
        user.setStatus("ACTIVE");

        AppUser saved = userRepository.save(user);
        auditLogService.logEvent(saved.getTenantId(), saved.getId(), "USER_CREATED", "SUCCESS", "User created: " + saved.getEmail());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public AppUserResponse getUserById(UUID tenantId, UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        
        // Tenant isolation check
        if (tenantId != null && user.getTenantId() != null && !tenantId.equals(user.getTenantId())) {
            throw new ResourceNotFoundException("User not found in tenant: " + userId);
        }
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<AppUserResponse> getUsersByTenant(UUID tenantId, Pageable pageable) {
        return userRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    public AppUserResponse mapToResponse(AppUser u) {
        return new AppUserResponse(
                u.getId(),
                u.getTenantId(),
                u.getEmail(),
                u.getRole(),
                u.getStatus(),
                u.getFailedLoginCount(),
                u.getLockedUntil(),
                u.getLastLoginAt(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
