package com.reviewai.security;

import com.reviewai.audit.AuditEventType;
import com.reviewai.audit.AuditLogService;
import com.reviewai.audit.AuditOutcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 403 for an authenticated caller whose role does not allow the URL (URL-level rules). */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final AuditLogService auditLogService;

    public RestAccessDeniedHandler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("method", request.getMethod());
        detail.put("path", request.getRequestURI());
        CurrentUser.get().ifPresent(u -> detail.put("role", u.role().name()));

        auditLogService.record(AuditEventType.ACCESS_DENIED_ROLE, AuditOutcome.DENIED,
            CurrentUser.get().map(AuthenticatedUser::tenantId).orElse(null),
            CurrentUser.get().map(AuthenticatedUser::userId).orElse(null),
            request.getRemoteAddr(), detail);

        SecurityErrorWriter.write(request, response, HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(), "Access is denied");
    }
}
