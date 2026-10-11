package com.customerreview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public record LeadCreateRequest(
    @NotNull(message = "Customer ID is required")
    UUID customerId,

    @Pattern(regexp = "NEW|CONTACTED|REVIEWED|OPTED_OUT", message = "Status must be NEW, CONTACTED, REVIEWED, or OPTED_OUT")
    String status,

    Instant eligibleAt
) {}
