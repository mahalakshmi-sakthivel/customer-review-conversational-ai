package com.customerreview.ai;

public interface ConversationalAiService {
    String generateResponse(String customerMessage, int turnCount);
    String classifySentiment(String reviewText, short rating);
}
