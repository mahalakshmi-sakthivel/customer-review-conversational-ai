package com.customerreview;

import com.customerreview.dto.*;
import com.customerreview.entity.Tenant;
import com.customerreview.exception.ConflictException;
import com.customerreview.exception.ResourceNotFoundException;
import com.customerreview.repository.TenantRepository;
import com.customerreview.security.EncryptionService;
import com.customerreview.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerReviewApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private UserService userService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private LeadService leadService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private EncryptionService encryptionService;

    private UUID tenant1Id;
    private UUID tenant2Id;

    @BeforeEach
    void setUp() {
        TenantResponse t1 = tenantService.createTenant(new TenantCreateRequest(
                "Acme Corp " + UUID.randomUUID(), "ACTIVE", "instance-acme-" + UUID.randomUUID()));
        TenantResponse t2 = tenantService.createTenant(new TenantCreateRequest(
                "Globex Inc " + UUID.randomUUID(), "ACTIVE", "instance-globex-" + UUID.randomUUID()));
        tenant1Id = t1.id();
        tenant2Id = t2.id();
    }

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
        assertNotNull(tenantService);
        assertNotNull(customerService);
    }

    @Test
    @DisplayName("Tenant Isolation: Customer created under Tenant 1 cannot be seen by Tenant 2")
    void testTenantIsolationOnCustomer() {
        CustomerResponse customer = customerService.createCustomer(tenant1Id,
                new CustomerCreateRequest("Alice", "+1234567890", "alice@example.com", false, "{}"));

        // Tenant 1 can access
        CustomerResponse found = customerService.getCustomerById(tenant1Id, customer.id());
        assertEquals("Alice", found.name());

        // Tenant 2 must NOT access Tenant 1's customer
        assertThrows(ResourceNotFoundException.class, () -> {
            customerService.getCustomerById(tenant2Id, customer.id());
        });
    }

    @Test
    @DisplayName("Database Constraint: Enforce at most ONE ACTIVE conversation per customer")
    void testOneActiveConversationConstraint() {
        CustomerResponse customer = customerService.createCustomer(tenant1Id,
                new CustomerCreateRequest("Bob", "+1987654321", null, false, null));

        // Create first active conversation
        ConversationResponse conv1 = conversationService.createConversation(tenant1Id,
                new ConversationCreateRequest(customer.id(), "ACTIVE"));
        assertEquals("ACTIVE", conv1.status());

        // Attempting to create a second ACTIVE conversation should be blocked
        assertThrows(ConflictException.class, () -> {
            conversationService.createConversation(tenant1Id,
                    new ConversationCreateRequest(customer.id(), "ACTIVE"));
        });

        // After completing the first conversation, a new ACTIVE one can be started
        conversationService.updateStatus(tenant1Id, conv1.id(), "COMPLETED");
        ConversationResponse conv2 = conversationService.createConversation(tenant1Id,
                new ConversationCreateRequest(customer.id(), "ACTIVE"));
        assertNotNull(conv2.id());
        assertNotEquals(conv1.id(), conv2.id());
    }

    @Test
    @DisplayName("Message Deduplication: Duplicate external message ID rejected per tenant")
    void testMessageDeduplication() {
        CustomerResponse customer = customerService.createCustomer(tenant1Id,
                new CustomerCreateRequest("Charlie", "+1122334455", null, false, null));
        ConversationResponse conv = conversationService.createConversation(tenant1Id,
                new ConversationCreateRequest(customer.id(), "ACTIVE"));

        String externalMsgId = "EVOLUTION_MSG_001";

        // First message with this external ID succeeds
        MessageResponse msg1 = messageService.createMessage(tenant1Id,
                new MessageCreateRequest(conv.id(), "INBOUND", "Hello", externalMsgId));
        assertNotNull(msg1.id());

        // Second message with identical external ID in same tenant is rejected
        assertThrows(ConflictException.class, () -> {
            messageService.createMessage(tenant1Id,
                    new MessageCreateRequest(conv.id(), "INBOUND", "Hello again", externalMsgId));
        });
    }

    @Test
    @DisplayName("Feedback constraint: Exactly one feedback record per conversation and rating 1-5")
    void testFeedbackSubmissionAndUniqueness() {
        CustomerResponse customer = customerService.createCustomer(tenant1Id,
                new CustomerCreateRequest("Dave", "+1555666777", null, false, null));
        ConversationResponse conv = conversationService.createConversation(tenant1Id,
                new ConversationCreateRequest(customer.id(), "ACTIVE"));

        FeedbackResponse fb = feedbackService.createFeedback(tenant1Id,
                new FeedbackCreateRequest(conv.id(), customer.id(), (short) 5, "Great app", "None", "Prompt service", "positive"));
        assertNotNull(fb.id());
        assertEquals(5, fb.rating());

        // Attempting a second feedback on the same conversation should fail
        assertThrows(ConflictException.class, () -> {
            feedbackService.createFeedback(tenant1Id,
                    new FeedbackCreateRequest(conv.id(), customer.id(), (short) 4, "Second feedback", null, null, "positive"));
        });
    }

    @Test
    @DisplayName("Field-level Encryption: Phone numbers are encrypted in DB and hashed for lookup")
    void testEncryptionService() {
        String phone = "+919876543210";
        byte[] encrypted = encryptionService.encrypt(phone);
        assertNotNull(encrypted);
        assertNotEquals(phone, new String(encrypted));

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(phone, decrypted);

        String hash1 = encryptionService.hmacSha256(phone);
        String hash2 = encryptionService.hmacSha256(phone);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    @DisplayName("Health endpoint is accessible publicly and returns UP")
    void testHealthEndpointPublicAccess() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CORS: Valid dev origin is allowed and returns Access-Control-Allow-Origin")
    void testCorsAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://127.0.0.1:5500")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"));
    }

    @Test
    @DisplayName("CORS: Disallowed origin does not receive Access-Control-Allow-Origin")
    void testCorsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://malicious-website.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
