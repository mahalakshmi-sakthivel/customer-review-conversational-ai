package com.customerreview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record MessageCreateRequest(
    @NotNull(message = "Conversation ID is required")
    UUID conversationId,

    @NotBlank(message = "Direction is required")
    @Pattern(regexp = "INBOUND|OUTBOUND", message = "Direction must be INBOUND or OUTBOUND")
    String direction,

    @NotBlank(message = "Content is required")
    String content,

    String externalMessageId
) {}
