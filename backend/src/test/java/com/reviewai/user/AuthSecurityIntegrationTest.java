package com.reviewai.user;

import com.reviewai.audit.AuditEventType;
import com.reviewai.audit.AuditLog;
import com.reviewai.audit.AuditLogRepository;
import com.reviewai.audit.AuditOutcome;
import com.reviewai.security.JwtService;
import com.reviewai.security.TenantContext;
import com.reviewai.tenant.Tenant;
import com.reviewai.tenant.TenantRepository;
import com.reviewai.tenant.TenantStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthSecurityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-9";

    @Autowired private WebApplicationContext context;
    @Autowired private AppUserRepository users;
    @Autowired private TenantRepository tenants;
    @Autowired private AuditLogRepository audits;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwtService;
    @Value("${app.jwt.secret}") private String jwtSecret;

    private MockMvc mvc;
    private Tenant tenantA;
    private Tenant tenantB;
    private AppUser superAdmin;
    private AppUser adminA;
    private AppUser opsA;
    private AppUser adminB;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        audits.deleteAll();
        users.deleteAll();
        tenants.deleteAll();
        TenantContext.clear();

        tenantA = tenants.save(new Tenant(null, "Tenant A", TenantStatus.ACTIVE, null));
        tenantB = tenants.save(new Tenant(null, "Tenant B", TenantStatus.ACTIVE, null));
        superAdmin = newUser(null, "super@example.com", Role.SUPER_ADMIN);
        adminA = newUser(tenantA, "admin-a@example.com", Role.TENANT_ADMIN);
        opsA = newUser(tenantA, "ops-a@example.com", Role.OPERATIONS_USER);
        adminB = newUser(tenantB, "admin-b@example.com", Role.TENANT_ADMIN);
    }

    private AppUser newUser(Tenant tenant, String email, Role role) {
        return users.save(new AppUser(UUID.randomUUID(), tenant == null ? null : tenant.getId(),
            email, encoder.encode(PASSWORD), role, UserStatus.ACTIVE));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String bearer(AppUser u) {
        return "Bearer " + jwtService.issue(u);
    }

    private List<AuditLog> events(AuditEventType type) {
        return audits.findAllByEventTypeOrderByCreatedAtAsc(type);
    }

    // ---------- login ----------

    @Test
    void validLoginReturns200WithTokenAndNoPasswordHash() throws Exception {
        String body = login("ADMIN-A@example.com", PASSWORD)   // email is case-insensitive
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.role").value("TENANT_ADMIN"))
            .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("$2");

        AppUser reloaded = users.findById(adminA.getId()).orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
        assertThat(events(AuditEventType.AUTH_LOGIN)).anyMatch(e -> e.getOutcome() == AuditOutcome.SUCCESS);
    }

    @Test
    void wrongPasswordAndUnknownUserGiveIdentical401() throws Exception {
        String wrong = login("admin-a@example.com", "nope").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"))
            .andReturn().getResponse().getContentAsString();
        String unknown = login("nobody@example.com", "nope").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"))
            .andReturn().getResponse().getContentAsString();
        assertThat(strip(wrong)).isEqualTo(strip(unknown));
    }

    private static String strip(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\",?", "").replaceAll("\"traceId\":\"[^\"]*\"", "");
    }

    @Test
    void disabledAccountRejectedEvenWithCorrectPassword() throws Exception {
        adminA.setStatus(UserStatus.DISABLED);
        users.save(adminA);
        login("admin-a@example.com", PASSWORD).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void lockoutAfterRepeatedFailuresThenExpiry() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("ops-a@example.com", "wrong").andExpect(status().isUnauthorized());
        }
        AppUser locked = users.findById(opsA.getId()).orElseThrow();
        assertThat(locked.getFailedLoginCount()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());
        assertThat(events(AuditEventType.AUTH_LOCKOUT)).hasSize(1);

        // correct password is still refused while locked
        login("ops-a@example.com", PASSWORD).andExpect(status().isUnauthorized());

        locked.setLockedUntil(Instant.now().minusSeconds(1));
        users.save(locked);
        login("ops-a@example.com", PASSWORD).andExpect(status().isOk());
        assertThat(users.findById(opsA.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        login("ops-a@example.com", "wrong").andExpect(status().isUnauthorized());
        login("ops-a@example.com", "wrong").andExpect(status().isUnauthorized());
        login("ops-a@example.com", PASSWORD).andExpect(status().isOk());
        assertThat(users.findById(opsA.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }

    @Test
    void loginValidationReturns400() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void auditEntriesNeverContainPasswordOrToken() throws Exception {
        login("admin-a@example.com", "super-secret-attempt").andExpect(status().isUnauthorized());
        for (AuditLog e : audits.findAll()) {
            assertThat(String.valueOf(e.getDetail())).doesNotContain("super-secret-attempt");
        }
        assertThat(events(AuditEventType.AUTH_LOGIN)).anyMatch(e -> e.getOutcome() == AuditOutcome.FAILURE);
    }

    // ---------- JWT on protected endpoints ----------

    @Test
    void validTokenAccepted() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(adminA.getId().toString()))
            .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()));
    }

    @Test
    void missingTokenReturns401WithEnvelopeAndAuditEntry() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
        assertThat(events(AuditEventType.AUTH_TOKEN_REJECTED)).hasSize(1);
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        String expired = Jwts.builder().id("j").subject(adminA.getId().toString())
            .claim("role", "TENANT_ADMIN").claim("tenantId", tenantA.getId().toString())
            .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
            .expiration(Date.from(Instant.now().minusSeconds(60)))
            .signWith(key, Jwts.SIG.HS256).compact();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
            .andExpect(status().isUnauthorized());
        assertThat(events(AuditEventType.AUTH_TOKEN_REJECTED).get(0).getDetail()).containsEntry("reason", "TOKEN_EXPIRED");
    }

    @Test
    void tamperedAndGarbageTokensReturn401() throws Exception {
        String[] parts = jwtService.issue(opsA).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1])).replace("OPERATIONS_USER", "SUPER_ADMIN");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + parts[2];
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Basic abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void algNoneTokenReturns401() throws Exception {
        String h = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String p = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("{\"sub\":\"" + superAdmin.getId() + "\",\"role\":\"SUPER_ADMIN\",\"jti\":\"x\",\"exp\":"
                + (Instant.now().getEpochSecond() + 600) + "}").getBytes());
        mvc.perform(get("/api/v1/tenants").header("Authorization", "Bearer " + h + "." + p + "."))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unmappedEndpointsAreDeniedByDefault() throws Exception {
        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customers").header("Authorization", bearer(adminA))).andExpect(status().isForbidden());
    }

    // ---------- roles ----------

    @Test
    void tenantsEndpointIsSuperAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/tenants")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tenants").header("Authorization", bearer(opsA))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tenants").header("Authorization", bearer(adminA))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tenants").header("Authorization", bearer(superAdmin))).andExpect(status().isOk());
        assertThat(events(AuditEventType.ACCESS_DENIED_ROLE)).hasSize(2);
    }

    @Test
    void operationsUserAndSuperAdminCannotUseTenantAdminEndpoint() throws Exception {
        mvc.perform(get("/api/v1/users/" + adminA.getId()).header("Authorization", bearer(opsA)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + adminA.getId()).header("Authorization", bearer(superAdmin)))
            .andExpect(status().isForbidden());
        assertThat(events(AuditEventType.ACCESS_DENIED_ROLE)).hasSize(2);
    }

    // ---------- tenant isolation ----------

    @Test
    void tenantAdminReadsOwnTenantUser() throws Exception {
        mvc.perform(get("/api/v1/users/" + opsA.getId()).header("Authorization", bearer(adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("ops-a@example.com"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void tenantACannotReadTenantBUserAndGets404() throws Exception {
        mvc.perform(get("/api/v1/users/" + adminB.getId()).header("Authorization", bearer(adminA)))
            .andExpect(status().isNotFound());
        List<AuditLog> denied = events(AuditEventType.ACCESS_DENIED_TENANT);
        assertThat(denied).hasSize(1);
        assertThat(denied.get(0).getTenantId()).isEqualTo(tenantA.getId());
        assertThat(denied.get(0).getUserId()).isEqualTo(adminA.getId());
    }

    @Test
    void unknownIdAlsoReturns404WithoutSecurityEvent() throws Exception {
        mvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header("Authorization", bearer(adminA)))
            .andExpect(status().isNotFound());
        assertThat(events(AuditEventType.ACCESS_DENIED_TENANT)).isEmpty();
    }

    @Test
    void clientSuppliedTenantIdIsIgnored() throws Exception {
        String bId = tenantB.getId().toString();
        // trying to reach tenant B's user by claiming to be tenant B
        mvc.perform(get("/api/v1/users/" + adminB.getId() + "?tenantId=" + bId)
                .header("X-Tenant-Id", bId).header("Authorization", bearer(adminA)))
            .andExpect(status().isNotFound());
        // own data is still served from the token's tenant
        mvc.perform(get("/api/v1/users/" + opsA.getId() + "?tenantId=" + bId)
                .header("X-Tenant-Id", bId).header("Authorization", bearer(adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()));
    }

    @Test
    void tenantContextIsClearedAfterEveryRequest() throws Exception {
        mvc.perform(get("/api/v1/users/" + opsA.getId()).header("Authorization", bearer(adminA))).andExpect(status().isOk());
        assertThat(TenantContext.getTenantId()).isNull();
        mvc.perform(get("/api/v1/users/" + adminB.getId()).header("Authorization", bearer(adminA))).andExpect(status().isNotFound());
        assertThat(TenantContext.getTenantId()).isNull();
        mvc.perform(get("/api/v1/tenants").header("Authorization", bearer(opsA))).andExpect(status().isForbidden());
        assertThat(TenantContext.getTenantId()).isNull();
    }
}
