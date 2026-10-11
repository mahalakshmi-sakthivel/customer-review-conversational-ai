package com.customerreview.controller;

import com.customerreview.ai.ConversationalAiService;
import com.customerreview.dto.ConversationResponse;
import com.customerreview.dto.FeedbackCreateRequest;
import com.customerreview.dto.MessageCreateRequest;
import com.customerreview.entity.Customer;
import com.customerreview.entity.Tenant;
import com.customerreview.repository.CustomerRepository;
import com.customerreview.repository.TenantRepository;
import com.customerreview.security.EncryptionService;
import com.customerreview.service.ConversationService;
import com.customerreview.service.FeedbackService;
import com.customerreview.service.MessageService;
import com.customerreview.whatsapp.WhatsAppProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final EncryptionService encryptionService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final FeedbackService feedbackService;
    private final ConversationalAiService aiService;
    private final WhatsAppProviderService whatsAppProvider;

    public WhatsAppWebhookController(TenantRepository tenantRepository,
                                     CustomerRepository customerRepository,
                                     EncryptionService encryptionService,
                                     ConversationService conversationService,
                                     MessageService messageService,
                                     FeedbackService feedbackService,
                                     ConversationalAiService aiService,
                                     WhatsAppProviderService whatsAppProvider) {
        this.tenantRepository = tenantRepository;
        this.customerRepository = customerRepository;
        this.encryptionService = encryptionService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.feedbackService = feedbackService;
        this.aiService = aiService;
        this.whatsAppProvider = whatsAppProvider;
    }

    /**
     * Webhook verification endpoint for WhatsApp / Meta / Evolution API.
     */
    @GetMapping("/whatsapp")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {
        log.info("Webhook verification received: mode={}, token={}", mode, token);
        if (challenge != null) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.ok("OK");
    }

    /**
     * Processes inbound WhatsApp message payloads safely.
     * Expects JSON:
     * {
     *   "instanceName": "tenant-instance-1",
     *   "phone": "+1234567890",
     *   "messageId": "msg_ext_12345",
     *   "text": "Great service! Rating 5"
     * }
     */
    @PostMapping("/whatsapp")
    public ResponseEntity<Map<String, Object>> handleInboundMessage(@RequestBody Map<String, Object> payload) {
        log.info("Inbound WhatsApp message payload received: {}", payload);

        String instanceName = (String) payload.get("instanceName");
        String senderPhone = (String) payload.get("phone");
        String messageId = (String) payload.get("messageId");
        String text = (String) payload.get("text");

        if (senderPhone == null || text == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "phone and text are required"));
        }

        // 1. Resolve tenant
        Optional<Tenant> tenantOpt = instanceName != null 
                ? tenantRepository.findByEvolutionInstanceName(instanceName)
                : tenantRepository.findAll().stream().findFirst();

        if (tenantOpt.isEmpty()) {
            log.warn("No tenant found matching instance: {}", instanceName);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "unknown_tenant"));
        }

        Tenant tenant = tenantOpt.get();
        UUID tenantId = tenant.getId();

        // 2. Resolve or create customer
        String phoneHash = encryptionService.hmacSha256(senderPhone);
        Customer customer = customerRepository.findByTenantIdAndPhoneHash(tenantId, phoneHash)
                .orElseGet(() -> {
                    Customer newCustomer = new Customer();
                    newCustomer.setTenantId(tenantId);
                    newCustomer.setPhoneHash(phoneHash);
                    newCustomer.setPhoneEncrypted(encryptionService.encrypt(senderPhone));
                    return customerRepository.save(newCustomer);
                });

        if (customer.isOptedOut()) {
            log.info("Customer {} is opted out. Dropping message.", customer.getId());
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "opted_out"));
        }

        // Check for unsubscribe request
        String lowerText = text.toLowerCase().trim();
        if (lowerText.contains("stop") || lowerText.contains("unsubscribe")) {
            customer.setOptedOut(true);
            customerRepository.save(customer);
            whatsAppProvider.sendMessage(tenantId, senderPhone, "You have been successfully unsubscribed.");
            return ResponseEntity.ok(Map.of("status", "opted_out"));
        }

        // 3. Find or create ACTIVE conversation
        ConversationResponse conversation = conversationService.getConversations(tenantId, "ACTIVE", org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().stream()
                .filter(c -> c.customerId().equals(customer.getId()))
                .findFirst()
                .orElseGet(() -> conversationService.createConversation(tenantId, new com.customerreview.dto.ConversationCreateRequest(customer.getId(), "ACTIVE")));

        // 4. Record INBOUND message (with deduplication)
        try {
            messageService.createMessage(tenantId, new MessageCreateRequest(conversation.id(), "INBOUND", text, messageId));
        } catch (Exception e) {
            log.warn("Message deduplication or insert skipped: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("status", "duplicate_or_ignored"));
        }

        // 5. Generate AI Response
        String botReply = aiService.generateResponse(text, conversation.turnCount() + 1);

        // 6. Record OUTBOUND message
        messageService.createMessage(tenantId, new MessageCreateRequest(conversation.id(), "OUTBOUND", botReply, null));

        // 7. Send message via WhatsApp provider
        whatsAppProvider.sendMessage(tenantId, senderPhone, botReply);

        // 8. If rating detected, save feedback and close conversation
        short rating = parseRating(text);
        if (rating > 0) {
            String sentiment = aiService.classifySentiment(text, rating);
            try {
                feedbackService.createFeedback(tenantId, new FeedbackCreateRequest(
                        conversation.id(),
                        customer.getId(),
                        rating,
                        text,
                        "Improve response time",
                        "High quality",
                        sentiment
                ));
                conversationService.updateStatus(tenantId, conversation.id(), "COMPLETED");
            } catch (Exception e) {
                log.info("Feedback recording handled: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "processed",
                "tenantId", tenantId,
                "conversationId", conversation.id(),
                "botReply", botReply
        ));
    }

    private short parseRating(String text) {
        if (text == null) return 0;
        for (char c : text.toCharArray()) {
            if (c >= '1' && c <= '5') {
                return (short) Character.getNumericValue(c);
            }
        }
        return 0;
    }
}
