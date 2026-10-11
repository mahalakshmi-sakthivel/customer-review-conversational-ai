
package com.customerreview.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public String hashPassword(String password) {
        if (password == null) {
            return null;
        }
        return bcrypt.encode(password);
    }

    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }

        try {
            if (storedHash.startsWith("$2a$")
                    || storedHash.startsWith("$2b$")
                    || storedHash.startsWith("$2y$")) {
                return bcrypt.matches(rawPassword, storedHash);
            }

            // Backward compatibility with the existing salt:SHA-256 format.
            if (!storedHash.contains(":")) {
                return false;
            }

            String[] parts = storedHash.split(":", 2);
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] actualHash =
                    md.digest(rawPassword.getBytes(StandardCharsets.UTF_8));

            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean needsUpgrade(String storedHash) {
        return storedHash != null && storedHash.contains(":");
    }
}
