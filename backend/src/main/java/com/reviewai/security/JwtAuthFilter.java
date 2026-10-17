package com.reviewai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request from its Bearer token.
 *
 * - A valid token puts an AuthenticatedUser into the SecurityContext and the tenant into TenantContext.
 * - Any problem leaves the request unauthenticated and records the reason on the request; the
 *   AuthenticationEntryPoint then answers 401 only if the endpoint is protected.
 * - TenantContext and the MDC entries are cleared in a finally block on every path.
 *
 * Deliberately NOT a Spring bean: it is created inside SecurityConfig so it runs once, inside
 * the security chain, and is not also auto-registered as a servlet filter.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String REJECT_REASON_ATTRIBUTE = "reviewai.auth.rejectReason";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            authenticate(request);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) {
            return;
        }
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            request.setAttribute(REJECT_REASON_ATTRIBUTE, "UNSUPPORTED_AUTH_SCHEME");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            request.setAttribute(REJECT_REASON_ATTRIBUTE, "EMPTY_TOKEN");
            return;
        }
        try {
            TokenClaims claims = jwtService.parse(token);
            AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.role(), claims.tenantId());
            SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()))));
            TenantContext.setTenantId(claims.tenantId());
            MDC.put("userId", claims.userId().toString());
            if (claims.tenantId() != null) {
                MDC.put("tenantId", claims.tenantId().toString());
            }
        } catch (InvalidTokenException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(REJECT_REASON_ATTRIBUTE, e.getReason());
        }
    }
}
