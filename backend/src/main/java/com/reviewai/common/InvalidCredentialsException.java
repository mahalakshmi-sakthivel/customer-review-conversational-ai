package com.reviewai.common;

/**
 * Thrown for every failed login (unknown user, wrong password, disabled or locked account).
 * The message is deliberately generic so callers cannot enumerate accounts. The real reason
 * is only written to the audit log.
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String GENERIC_MESSAGE = "Invalid email or password";

    public InvalidCredentialsException() {
        super(GENERIC_MESSAGE);
    }
}
