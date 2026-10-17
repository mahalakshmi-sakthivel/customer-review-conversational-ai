package com.reviewai.user;

import com.reviewai.audit.AuditEventType;
import com.reviewai.audit.AuditLogService;
import com.reviewai.audit.AuditOutcome;
import com.reviewai.common.ResourceNotFoundException;
import com.reviewai.security.AuthenticatedUser;
import com.reviewai.security.CurrentUser;
import com.reviewai.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Reference implementation of the tenant-isolation pattern for later modules:
 * tenant comes from CurrentUser, lookups are findByIdAndTenantId, and an id that exists in
 * another tenant is reported as 404 so existence is not leaked.
 */
@Service
public class UserService {

    private final AppUserRepository users;
    private final AuditLogService audit;

    public UserService(AppUserRepository users, AuditLogService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public UserResponse getUserInCallerTenant(UUID id) {
        AuthenticatedUser caller = CurrentUser.require();
        UUID tenantId = CurrentUser.requireTenantId();

        return users.findByIdAndTenantId(id, tenantId)
            .map(UserResponse::from)
            .orElseThrow(() -> {
                // existsById returns only a boolean and is used solely to raise the security event.
                if (users.existsById(id)) {
                    audit.record(AuditEventType.ACCESS_DENIED_TENANT, AuditOutcome.DENIED,
                        tenantId, caller.userId(), null, Map.of("resource", "app_user"));
                }
                return new ResourceNotFoundException("User not found");
            });
    }
}
