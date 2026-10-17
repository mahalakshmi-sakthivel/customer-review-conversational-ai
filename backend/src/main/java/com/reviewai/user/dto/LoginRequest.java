package com.reviewai.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "email is required") @Size(max = 255, message = "email is too long") String email,
    @NotBlank(message = "password is required") @Size(max = 128, message = "password is too long") String password
) {
    /** Records print every field by default; never let the password reach a log line. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
