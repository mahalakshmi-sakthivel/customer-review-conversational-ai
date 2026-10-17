package com.reviewai.common;

import com.reviewai.audit.AuditEventType;
import com.reviewai.audit.AuditLogService;
import com.reviewai.audit.AuditOutcome;
import com.reviewai.security.AuthenticatedUser;
import com.reviewai.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized exception handler producing the PRD §7.4 frozen error envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Optional so the handler can still be built with `new` in standalone MockMvc tests. */
    @Autowired(required = false)
    private AuditLogService auditLogService;

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        String traceId = getTraceId();
        ApiError error = new ApiError(
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            InvalidCredentialsException.GENERIC_MESSAGE,
            request.getRequestURI(),
            traceId
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.warn("[{}] Resource not found: {}", traceId, ex.getMessage());
        ApiError error = new ApiError(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI(),
            traceId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.warn("[{}] Conflict: {}", traceId, ex.getMessage());
        ApiError error = new ApiError(
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI(),
            traceId
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = getTraceId();
        List<ApiError.ValidationError> validationErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::mapFieldError)
            .toList();

        String summary = validationErrors.isEmpty()
            ? "Validation failed"
            : validationErrors.get(0).field() + ": " + validationErrors.get(0).message();

        log.warn("[{}] Validation error on {}: {}", traceId, request.getRequestURI(), summary);
        ApiError error = new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            summary,
            request.getRequestURI(),
            traceId,
            validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.warn("[{}] Malformed request body on {}: {}", traceId, request.getRequestURI(), ex.getMessage());
        ApiError error = new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Malformed JSON request body",
            request.getRequestURI(),
            traceId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.warn("[{}] Access denied on {}", traceId, request.getRequestURI());
        if (auditLogService != null) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", request.getMethod());
            detail.put("path", request.getRequestURI());
            CurrentUser.get().ifPresent(u -> detail.put("role", u.role().name()));
            auditLogService.record(AuditEventType.ACCESS_DENIED_ROLE, AuditOutcome.DENIED,
                CurrentUser.get().map(AuthenticatedUser::tenantId).orElse(null),
                CurrentUser.get().map(AuthenticatedUser::userId).orElse(null),
                request.getRemoteAddr(), detail);
        }
        ApiError error = new ApiError(
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            "Access is denied",
            request.getRequestURI(),
            traceId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.error("[{}] Unhandled exception on {}", traceId, request.getRequestURI(), ex);
        ApiError error = new ApiError(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "An unexpected internal error occurred",
            request.getRequestURI(),
            traceId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private ApiError.ValidationError mapFieldError(FieldError fieldError) {
        return new ApiError.ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private String getTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return traceId != null ? traceId : "";
    }
}
