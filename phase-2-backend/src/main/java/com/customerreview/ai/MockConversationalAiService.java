package com.customerreview.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MockConversationalAiService implements ConversationalAiService {

    private static final Logger log = LoggerFactory.getLogger(MockConversationalAiService.class);

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Override
    public String generateResponse(String customerMessage, int turnCount) {
        log.info("[AI SERVICE] Processing turn {}: {}", turnCount, customerMessage);
        if (customerMessage == null || customerMessage.isBlank()) {
            return "Hello! How would you rate your recent experience with us from 1 to 5?";
        }

        String lower = customerMessage.toLowerCase().trim();
        if (lower.contains("stop") || lower.contains("unsubscribe")) {
            return "You have been unsubscribed. You will not receive further messages. Thank you!";
        }

        if (turnCount <= 1) {
            return "Thank you for getting back to us! Could you share what went well or what we can improve?";
        } else if (turnCount == 2) {
            return "We truly appreciate your detailed feedback. Is there anything else our team should know?";
        } else {
            return "Thank you so much for your time and honest review! Have a wonderful day!";
        }
    }

    @Override
    public String classifySentiment(String reviewText, short rating) {
        if (rating >= 4) {
            return "positive";
        } else if (rating == 3) {
            return "neutral";
        } else if (rating >= 1) {
            return "negative";
        }
        return "unknown";
    }
}
