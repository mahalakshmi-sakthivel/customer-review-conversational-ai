package com.reviewai.audit;

import com.reviewai.common.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Records security events. Never throws: an audit failure must not turn a normal
 * response into an error. Callers must pass only safe detail values (no passwords,
 * tokens, secrets or customer content); keys that look sensitive are dropped as a
 * second line of defence.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_DETAIL_VALUE_LENGTH = 200;

    private final AuditLogWriter writer;

    public AuditLogService(AuditLogWriter writer) {
        this.writer = writer;
    }

    /**
     * @param ipAddress remote address, or null to take it from the current request if there is one
     */
    public void record(AuditEventType type, AuditOutcome outcome, UUID tenantId, UUID userId,
                       String ipAddress, Map<String, Object> detail) {
        try {
            String ip = ipAddress != null ? ipAddress : currentRequestIp();
            writer.write(new AuditLog(
                type, outcome, tenantId, userId,
                truncate(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY), 64),
                truncate(ip, 45),
                sanitize(detail)));
        } catch (RuntimeException e) {
            // Class name only: the message of a persistence error can contain values.
            log.error("Failed to persist audit event {} ({}): {}", type, outcome, e.getClass().getSimpleName());
        }
    }

    private static String currentRequestIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static Map<String, Object> sanitize(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        detail.forEach((key, value) -> {
            String k = key.toLowerCase(Locale.ROOT);
            if (k.contains("password") || k.contains("token") || k.contains("secret") || k.contains("authorization")) {
                return;
            }
            safe.put(key, value instanceof String s ? truncate(s, MAX_DETAIL_VALUE_LENGTH) : value);
        });
        return safe.isEmpty() ? null : safe;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
