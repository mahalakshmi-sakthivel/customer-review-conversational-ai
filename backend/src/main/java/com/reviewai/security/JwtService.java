package com.reviewai.security;

import com.reviewai.user.AppUser;
import com.reviewai.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and validates access tokens. HS256 only.
 * Claims: sub (userId), role, tenantId (absent for SUPER_ADMIN), iat, exp, jti.
 * No phone, email or business data goes into the token (Phase 3 section 2).
 */
@Service
public class JwtService {

    private static final String ALGORITHM = "HS256";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TENANT_ID = "tenantId";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(
        @Value("${app.jwt.secret}") String base64Secret,
        @Value("${app.jwt.access-token-minutes:15}") long accessTokenMinutes,
        Clock clock
    ) {
        byte[] secretBytes;
        try {
            secretBytes = Decoders.BASE64.decode(base64Secret == null ? "" : base64Secret.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be Base64 encoded");
        }
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must decode to at least 32 bytes");
        }
        if (accessTokenMinutes < 1 || accessTokenMinutes > 60) {
            throw new IllegalStateException("app.jwt.access-token-minutes must be between 1 and 60");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
        this.clock = clock;
        this.parser = Jwts.parser()
            .verifyWith(key)
            .clock(() -> Date.from(clock.instant()))
            .build();
    }

    public String issue(AppUser user) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(user.getId().toString())
            .claim(CLAIM_ROLE, user.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtl)));
        if (user.getTenantId() != null) {
            builder.claim(CLAIM_TENANT_ID, user.getTenantId().toString());
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /**
     * Validates signature, algorithm, expiry and claim shape.
     * @throws InvalidTokenException for anything that is not a fully valid token
     */
    public TokenClaims parse(String token) {
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                throw new InvalidTokenException("UNSUPPORTED_ALGORITHM");
            }
            Claims claims = jws.getPayload();

            String subject = claims.getSubject();
            String roleName = claims.get(CLAIM_ROLE, String.class);
            String jti = claims.getId();
            Date expiration = claims.getExpiration();
            if (subject == null || roleName == null || jti == null || expiration == null) {
                throw new InvalidTokenException("TOKEN_INVALID");
            }

            Role role = Role.valueOf(roleName);
            String tenantClaim = claims.get(CLAIM_TENANT_ID, String.class);
            UUID tenantId = tenantClaim == null ? null : UUID.fromString(tenantClaim);
            if (role != Role.SUPER_ADMIN && tenantId == null) {
                throw new InvalidTokenException("TENANT_CLAIM_MISSING");
            }
            return new TokenClaims(UUID.fromString(subject), role, tenantId, jti, expiration.toInstant());
        } catch (InvalidTokenException e) {
            throw e;
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("TOKEN_EXPIRED");
        } catch (JwtException | IllegalArgumentException | ClassCastException e) {
            throw new InvalidTokenException("TOKEN_INVALID");
        }
    }
}
