package com.reviewai.user;

import com.reviewai.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Only the tenant-scoped detail read needed to prove isolation. Listing, pagination and user
 * management belong to later phases. Any tenantId in the query string or headers is ignored.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Tenant-scoped user lookup")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get a user in the caller's tenant", description = "404 if the id is unknown or belongs to another tenant")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserInCallerTenant(id));
    }
}
