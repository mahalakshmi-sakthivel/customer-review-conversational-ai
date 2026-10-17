package com.reviewai.security;

import com.reviewai.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Writes the frozen ApiError envelope for errors raised inside the security filter chain. */
final class SecurityErrorWriter {

    private SecurityErrorWriter() {}

    static void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String error, String message) throws IOException {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        String body = "{\"timestamp\":\"" + Instant.now()
            + "\",\"status\":" + status
            + ",\"error\":\"" + escape(error)
            + "\",\"message\":\"" + escape(message)
            + "\",\"path\":\"" + escape(request.getRequestURI())
            + "\",\"traceId\":\"" + escape(traceId == null ? "" : traceId) + "\"}";
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
