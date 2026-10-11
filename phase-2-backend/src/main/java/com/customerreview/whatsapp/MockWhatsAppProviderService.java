package com.customerreview.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MockWhatsAppProviderService implements WhatsAppProviderService {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppProviderService.class);

    @Value("${app.whatsapp.api-url:}")
    private String apiUrl;

    @Value("${app.whatsapp.api-token:}")
    private String apiToken;

    @Override
    public void sendMessage(UUID tenantId, String recipientPhone, String messageContent) {
        if (isConfigured()) {
            log.info("[LIVE WHATSAPP API] Sending message to {} for tenant {}: {}", recipientPhone, tenantId, messageContent);
            // In live mode with credentials, call Evolution / Meta Cloud API endpoint here
        } else {
            log.info("[SANDBOX / MOCK WHATSAPP] Simulating delivery to {} for tenant {}: {}", recipientPhone, tenantId, messageContent);
        }
    }

    @Override
    public boolean isConfigured() {
        return apiUrl != null && !apiUrl.isBlank() && apiToken != null && !apiToken.isBlank();
    }
}
