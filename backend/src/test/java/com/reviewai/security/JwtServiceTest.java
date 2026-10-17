package com.reviewai.security;

import com.reviewai.user.AppUser;
import com.reviewai.user.Role;
import com.reviewai.user.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "dGhpcy1pcy1hLXNlY3JldC1qd3Qta2V5LWZvci10ZXN0aW5nLTM4NGJpdHMtbG9uZyE=";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

    private final JwtService service = new JwtService(SECRET, 15, Clock.systemUTC());

    private AppUser user(Role role, UUID tenantId) {
        return new AppUser(UUID.randomUUID(), tenantId, "u@example.com", "hash", role, UserStatus.ACTIVE);
    }

    private String reason(String token) {
        return catchInvalid(token).getReason();
    }

    private InvalidTokenException catchInvalid(String token) {
        try {
            service.parse(token);
        } catch (InvalidTokenException e) {
            return e;
        }
        throw new AssertionError("token was accepted");
    }

    @Test
    void roundTripKeepsOnlyIdentityClaims() {
        UUID tenant = UUID.randomUUID();
        AppUser u = user(Role.TENANT_ADMIN, tenant);
        TokenClaims c = service.parse(service.issue(u));
        assertThat(c.userId()).isEqualTo(u.getId());
        assertThat(c.role()).isEqualTo(Role.TENANT_ADMIN);
        assertThat(c.tenantId()).isEqualTo(tenant);
        assertThat(c.jti()).isNotBlank();
    }

    @Test
    void tokenPayloadContainsNoEmailOrBusinessData() {
        String token = service.issue(user(Role.TENANT_ADMIN, UUID.randomUUID()));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(payload).doesNotContain("email").doesNotContain("u@example.com");
    }

    @Test
    void superAdminTokenHasNoTenant() {
        TokenClaims c = service.parse(service.issue(user(Role.SUPER_ADMIN, null)));
        assertThat(c.tenantId()).isNull();
        assertThat(c.role()).isEqualTo(Role.SUPER_ADMIN);
    }

    @Test
    void expiredTokenRejected() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String old = new JwtService(SECRET, 15, past).issue(user(Role.TENANT_ADMIN, UUID.randomUUID()));
        assertThat(reason(old)).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void tamperedPayloadRejected() {
        String token = service.issue(user(Role.OPERATIONS_USER, UUID.randomUUID()));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]))
            .replace("OPERATIONS_USER", "SUPER_ADMIN");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + parts[2];
        assertThat(reason(forged)).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void algorithmNoneRejected() {
        String enc = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("{\"sub\":\"" + UUID.randomUUID() + "\",\"role\":\"SUPER_ADMIN\",\"jti\":\"x\",\"exp\":"
                + (Instant.now().getEpochSecond() + 600) + "}").getBytes());
        assertThat(reason(enc + "." + body + ".")).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void wrongSecretRejected() {
        SecretKey other = Keys.hmacShaKeyFor("another-secret-another-secret-another-secret".getBytes());
        String token = Jwts.builder().id("j").subject(UUID.randomUUID().toString())
            .claim("role", "SUPER_ADMIN")
            .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(other, Jwts.SIG.HS256).compact();
        assertThat(reason(token)).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void nonHs256AlgorithmRejectedEvenWithCorrectKey() {
        String token = Jwts.builder().id("j").subject(UUID.randomUUID().toString())
            .claim("role", "SUPER_ADMIN")
            .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(KEY, Jwts.SIG.HS384).compact();
        assertThat(reason(token)).isEqualTo("UNSUPPORTED_ALGORITHM");
    }

    @Test
    void tokenWithoutExpiryRejected() {
        String token = Jwts.builder().id("j").subject(UUID.randomUUID().toString())
            .claim("role", "SUPER_ADMIN").signWith(KEY, Jwts.SIG.HS256).compact();
        assertThat(reason(token)).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void tenantRoleWithoutTenantClaimRejected() {
        String token = Jwts.builder().id("j").subject(UUID.randomUUID().toString())
            .claim("role", "TENANT_ADMIN")
            .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(KEY, Jwts.SIG.HS256).compact();
        assertThat(reason(token)).isEqualTo("TENANT_CLAIM_MISSING");
    }

    @Test
    void unknownRoleAndGarbageRejected() {
        String token = Jwts.builder().id("j").subject(UUID.randomUUID().toString())
            .claim("role", "ROOT")
            .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(KEY, Jwts.SIG.HS256).compact();
        assertThat(reason(token)).isEqualTo("TOKEN_INVALID");
        assertThat(reason("not.a.jwt")).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void weakOrMalformedSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("c2hvcnQ=", 15, Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("***not base64***", 15, Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class);
    }
}
