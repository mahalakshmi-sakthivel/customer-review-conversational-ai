package com.customerreview.whatsapp;

import java.util.UUID;

public interface WhatsAppProviderService {
    void sendMessage(UUID tenantId, String recipientPhone, String messageContent);
    boolean isConfigured();
}
