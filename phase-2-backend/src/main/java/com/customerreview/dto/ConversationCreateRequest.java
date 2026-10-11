package com.customerreview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record ConversationCreateRequest(
    @NotNull(message = "Customer ID is required")
    UUID customerId,

    @Pattern(regexp = "ACTIVE|COMPLETED|ABANDONED|ESCALATED|OPTED_OUT", message = "Status must be ACTIVE, COMPLETED, ABANDONED, ESCALATED, or OPTED_OUT")
    String status
) {}
