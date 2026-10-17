package com.reviewai.common;

import com.reviewai.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that establishes the X-Trace-Id header and MDC context for logging (NFR-OBS).
 * Clears MDC and TenantContext on request completion to prevent ThreadLocal leaks.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final java.util.regex.Pattern TRACE_ID_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        // Accept a client trace id only if it is short and made of safe characters. This keeps
        // log lines and audit_log.trace_id (VARCHAR(64)) clean.
        if (!StringUtils.hasText(traceId) || !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
            TenantContext.clear();
        }
    }
}
