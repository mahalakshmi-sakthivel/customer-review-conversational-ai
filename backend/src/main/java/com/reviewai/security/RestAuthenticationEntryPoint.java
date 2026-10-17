package com.reviewai.security;

import com.reviewai.audit.AuditEventType;
import com.reviewai.audit.AuditLogService;
import com.reviewai.audit.AuditOutcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 401 for any protected request without a valid identity. The client always sees the same
 * message; the specific reason (missing, expired, tampered ...) only goes to the audit log.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String MISSING_TOKEN = "MISSING_TOKEN";

    private final AuditLogService auditLogService;

    public RestAuthenticationEntryPoint(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthFilter.REJECT_REASON_ATTRIBUTE);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", reason != null ? reason.toString() : MISSING_TOKEN);
        detail.put("method", request.getMethod());
        detail.put("path", request.getRequestURI());
        auditLogService.record(AuditEventType.AUTH_TOKEN_REJECTED, AuditOutcome.DENIED,
            null, null, request.getRemoteAddr(), detail);

        SecurityErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "Full authentication is required to access this resource");
    }
}
