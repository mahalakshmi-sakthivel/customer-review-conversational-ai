package com.reviewai.user.dto;

import com.reviewai.user.Role;

import java.util.UUID;

/** Identity as seen by the backend after JWT validation. */
public record MeResponse(UUID userId, Role role, UUID tenantId) {}
