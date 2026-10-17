package com.reviewai.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Standardized API Error Response Envelope conforming to PRD §7.4.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String traceId,
    List<ValidationError> errors
) {
    public ApiError(int status, String error, String message, String path, String traceId) {
        this(Instant.now(), status, error, message, path, traceId, null);
    }

    public ApiError(int status, String error, String message, String path, String traceId, List<ValidationError> errors) {
        this(Instant.now(), status, error, message, path, traceId, errors);
    }

    public record ValidationError(String field, String message) {}
}
