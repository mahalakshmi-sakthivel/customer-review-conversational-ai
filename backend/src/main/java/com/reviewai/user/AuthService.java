package com.reviewai.user;

import com.reviewai.audit.AuditEventType;
import com.reviewai.audit.AuditLogService;
import com.reviewai.audit.AuditOutcome;
import com.reviewai.common.InvalidCredentialsException;
import com.reviewai.security.JwtService;
import com.reviewai.user.dto.LoginRequest;
import com.reviewai.user.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Login. Every failure throws the same InvalidCredentialsException; the real reason goes to the
 * audit log only. Lockout thresholds are PROPOSED defaults (5 failures, 15 minutes), pending approval.
 */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService audit;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final long lockMinutes;
    /** Used to spend the same hashing time when the email is unknown. */
    private final String dummyHash;

    public AuthService(
        AppUserRepository users,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuditLogService audit,
        Clock clock,
        @Value("${app.security.login.max-failed-attempts:5}") int maxFailedAttempts,
        @Value("${app.security.login.lock-minutes:15}") long lockMinutes
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** noRollbackFor: the failed-attempt counter must be saved even though we throw. */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse login(LoginRequest request, String ipAddress) {
        AppUser user = users.findByEmailForLogin(request.email().trim()).orElse(null);

        if (user == null) {
            matches(request.password(), dummyHash);
            audit.record(AuditEventType.AUTH_LOGIN, AuditOutcome.FAILURE, null, null, ipAddress,
                Map.of("reason", "UNKNOWN_USER"));
            throw new InvalidCredentialsException();
        }

        boolean passwordOk = matches(request.password(), user.getPasswordHash());
        Instant now = clock.instant();

        if (user.getStatus() != UserStatus.ACTIVE) {
            fail(user, ipAddress, "ACCOUNT_DISABLED");
        }

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                fail(user, ipAddress, "ACCOUNT_LOCKED");
            }
            // lock has expired: start a fresh count
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }

        if (!passwordOk) {
            int attempts = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(attempts);
            boolean locked = attempts >= maxFailedAttempts;
            if (locked) {
                user.setLockedUntil(now.plusSeconds(lockMinutes * 60));
            }
            users.save(user);
            if (locked) {
                audit.record(AuditEventType.AUTH_LOCKOUT, AuditOutcome.DENIED, user.getTenantId(), user.getId(),
                    ipAddress, Map.of("failedAttempts", attempts));
            }
            audit.record(AuditEventType.AUTH_LOGIN, AuditOutcome.FAILURE, user.getTenantId(), user.getId(),
                ipAddress, Map.of("reason", "BAD_PASSWORD"));
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        users.save(user);

        String token = jwtService.issue(user);
        audit.record(AuditEventType.AUTH_LOGIN, AuditOutcome.SUCCESS, user.getTenantId(), user.getId(),
            ipAddress, null);

        return new LoginResponse(token, "Bearer", jwtService.getAccessTokenTtlSeconds(),
            user.getId(), user.getTenantId(), user.getEmail(), user.getRole());
    }

    private void fail(AppUser user, String ipAddress, String reason) {
        // Counters are intentionally not touched for disabled or locked accounts.
        users.save(user);
        audit.record(AuditEventType.AUTH_LOGIN, AuditOutcome.FAILURE, user.getTenantId(), user.getId(),
            ipAddress, Map.of("reason", reason));
        throw new InvalidCredentialsException();
    }

    private boolean matches(String raw, String hash) {
        try {
            return passwordEncoder.matches(raw, hash);
        } catch (IllegalArgumentException e) {
            // e.g. password longer than BCrypt's 72-byte limit
            return false;
        }
    }
}
