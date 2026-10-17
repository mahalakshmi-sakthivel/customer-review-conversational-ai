package com.reviewai.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewai.common.ConflictException;
import com.reviewai.common.GlobalExceptionHandler;
import com.reviewai.common.PageResponse;
import com.reviewai.common.ResourceNotFoundException;
import com.reviewai.common.TraceIdFilter;
import com.reviewai.tenant.dto.CreateTenantRequest;
import com.reviewai.tenant.dto.TenantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private TenantController tenantController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(tenantController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .addFilter(new TraceIdFilter())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenants - 201 Created on valid payload with X-Trace-Id")
    void createTenantReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        CreateTenantRequest request = new CreateTenantRequest("RetailHub", "retail_instance");
        TenantResponse response = new TenantResponse(id, "RetailHub", TenantStatus.ACTIVE, "retail_instance", Instant.now(), Instant.now());

        when(tenantService.createTenant(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/tenants")
                .header("X-Trace-Id", "trace-test-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-Trace-Id", "trace-test-123"))
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("RetailHub"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.evolutionInstanceName").value("retail_instance"));
    }

    @Test
    @DisplayName("POST /api/v1/tenants - 400 Bad Request when tenant name is blank")
    void createTenantBlankNameReturns400() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("", null);

        mockMvc.perform(post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message", containsString("Tenant name is required")))
            .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/v1/tenants - 409 Conflict when tenant name already exists")
    void createTenantDuplicateReturns409() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("RetailHub", null);

        when(tenantService.createTenant(any()))
            .thenThrow(new ConflictException("Tenant with name 'RetailHub' already exists"));

        mockMvc.perform(post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("Tenant with name 'RetailHub' already exists"));
    }

    @Test
    @DisplayName("GET /api/v1/tenants/{id} - 200 OK when tenant exists")
    void getTenantByIdReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        TenantResponse response = new TenantResponse(id, "RetailHub", TenantStatus.ACTIVE, null, Instant.now(), Instant.now());

        when(tenantService.getTenantById(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/tenants/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("RetailHub"));
    }

    @Test
    @DisplayName("GET /api/v1/tenants/{id} - 404 Not Found returns PRD §7.4 envelope")
    void getTenantByIdNotFoundReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(tenantService.getTenantById(id))
            .thenThrow(new ResourceNotFoundException("Tenant not found with ID: " + id));

        mockMvc.perform(get("/api/v1/tenants/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message", containsString(id.toString())))
            .andExpect(jsonPath("$.path").value("/api/v1/tenants/" + id))
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/v1/tenants - 200 OK returns paginated response")
    void getTenantsReturnsPaginated() throws Exception {
        UUID id = UUID.randomUUID();
        TenantResponse item = new TenantResponse(id, "RetailHub", TenantStatus.ACTIVE, null, Instant.now(), Instant.now());
        PageResponse<TenantResponse> pageResponse = new PageResponse<>(
            List.of(item), 0, 20, 1, 1, true, true
        );

        when(tenantService.getTenants(any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/tenants?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/tenants/{id} - 204 No Content")
    void deleteTenantReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(tenantService).deleteTenant(id);

        mockMvc.perform(delete("/api/v1/tenants/{id}", id))
            .andExpect(status().isNoContent());
    }
}
