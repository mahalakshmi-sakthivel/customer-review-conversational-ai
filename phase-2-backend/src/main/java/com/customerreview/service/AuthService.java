
package com.customerreview.service;

import com.customerreview.dto.LoginResponse;
import com.customerreview.entity.AppUser;
import com.customerreview.repository.AppUserRepository;
import com.customerreview.security.JwtService;
import com.customerreview.security.PasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final AppUserRepository userRepository;
    private final PasswordService passwordService;
    private final AuditLogService auditLogService;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordService passwordService,
            AuditLogService auditLogService,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.auditLogService = auditLogService;
        this.jwtService = jwtService;
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public LoginResponse login(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim();

        AppUser user = userRepository.findByEmailLower(normalizedEmail)
                .orElse(null);

        if (user == null) {
            auditLogService.logEvent(
                    null, null, "LOGIN", "FAILURE", "Invalid credentials");
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            auditLogService.logEvent(
                    user.getTenantId(), user.getId(),
                    "LOGIN", "FAILURE", "Account disabled");
            throw new IllegalArgumentException("Invalid email or password");
        }

        Instant now = Instant.now();

        if (user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(now)) {
            auditLogService.logEvent(
                    user.getTenantId(), user.getId(),
                    "LOGIN", "FAILURE", "Account temporarily locked");
            throw new IllegalArgumentException(
                    "Account temporarily locked. Try again later.");
        }

        if (user.getLockedUntil() != null
                && !user.getLockedUntil().isAfter(now)) {
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }

        if (!passwordService.verifyPassword(
                password, user.getPasswordHash())) {
            int attempts = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plusSeconds(LOCK_MINUTES * 60));
            }

            userRepository.save(user);
            auditLogService.logEvent(
                    user.getTenantId(), user.getId(),
                    "LOGIN", "FAILURE", "Invalid password");

            throw new IllegalArgumentException("Invalid email or password");
        }

        if (passwordService.needsUpgrade(user.getPasswordHash())) {
            user.setPasswordHash(passwordService.hashPassword(password));
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);

        AppUser savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser);

        auditLogService.logEvent(
                savedUser.getTenantId(), savedUser.getId(),
                "LOGIN", "SUCCESS", "Login successful");

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationMs() / 1000,
                savedUser.getId(),
                savedUser.getTenantId(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }
}
