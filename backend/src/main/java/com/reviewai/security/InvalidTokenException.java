package com.reviewai.security;

/** The reason is an internal code for the audit log. It is never sent to the client. */
public class InvalidTokenException extends RuntimeException {

    private final String reason;

    public InvalidTokenException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
