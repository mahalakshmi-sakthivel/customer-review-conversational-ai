package com.customerreview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record FeedbackCreateRequest(
    @NotNull(message = "Conversation ID is required")
    UUID conversationId,

    @NotNull(message = "Customer ID is required")
    UUID customerId,

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    short rating,

    String problem,

    String suggestion,

    String expectation,

    @NotBlank(message = "Sentiment is required")
    @Pattern(regexp = "positive|neutral|negative|mixed|unknown", message = "Sentiment must be positive, neutral, negative, mixed, or unknown")
    String sentiment
) {}
