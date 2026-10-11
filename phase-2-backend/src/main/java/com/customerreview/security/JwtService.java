
package com.customerreview.security;

import com.customerreview.entity.AppUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import io.jsonwebtoken.Claims;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-ms}") long expirationMs) {

        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(AppUser user) {
        Instant now = Instant.now();

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)));

        if (user.getTenantId() != null) {
            builder.claim("tenantId", user.getTenantId().toString());
        }

        return builder
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

public Claims extractClaims(String token) {
    return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
}

public boolean isValidToken(String token) {
    try {
        Claims claims = extractClaims(token);
        UUID.fromString(claims.getSubject());
        return claims.getExpiration() != null
                && claims.getExpiration().after(new Date());
    } catch (Exception e) {
        return false;
    }
}
}
