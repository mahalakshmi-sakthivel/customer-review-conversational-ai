package com.reviewai.tenant;

import com.reviewai.common.ConflictException;
import com.reviewai.common.PageResponse;
import com.reviewai.common.ResourceNotFoundException;
import com.reviewai.tenant.dto.CreateTenantRequest;
import com.reviewai.tenant.dto.TenantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository);
    }

    @Test
    @DisplayName("Should successfully create a new tenant")
    void createTenantSuccess() {
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme_instance");

        when(tenantRepository.existsByName("Acme Corp")).thenReturn(false);
        when(tenantRepository.existsByEvolutionInstanceName("acme_instance")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            return t;
        });

        TenantResponse response = tenantService.createTenant(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Acme Corp");
        assertThat(response.evolutionInstanceName()).isEqualTo("acme_instance");
        assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when tenant name already exists")
    void createTenantDuplicateName() {
        CreateTenantRequest request = new CreateTenantRequest("Existing Corp", null);
        when(tenantRepository.existsByName("Existing Corp")).thenReturn(true);

        assertThatThrownBy(() -> tenantService.createTenant(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Existing Corp");
    }

    @Test
    @DisplayName("Should throw ConflictException when evolution instance already in use")
    void createTenantDuplicateEvolutionInstance() {
        CreateTenantRequest request = new CreateTenantRequest("New Corp", "duplicate_instance");
        when(tenantRepository.existsByName("New Corp")).thenReturn(false);
        when(tenantRepository.existsByEvolutionInstanceName("duplicate_instance")).thenReturn(true);

        assertThatThrownBy(() -> tenantService.createTenant(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("duplicate_instance");
    }

    @Test
    @DisplayName("Should return TenantResponse when tenant is found by ID")
    void getTenantByIdSuccess() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant(id, "Acme", TenantStatus.ACTIVE, "acme_inst");
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        TenantResponse response = tenantService.getTenantById(id);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when tenant ID not found")
    void getTenantByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenantById(id))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("Should return paginated tenants")
    void getTenantsPaginated() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant(id, "Acme", TenantStatus.ACTIVE, "acme_inst");
        Pageable pageable = PageRequest.of(0, 10);
        when(tenantRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(tenant), pageable, 1));

        PageResponse<TenantResponse> response = tenantService.getTenants(pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).name()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("Should soft-delete tenant by setting status to DELETED")
    void deleteTenantSuccess() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant(id, "Acme", TenantStatus.ACTIVE, "acme_inst");
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        tenantService.deleteTenant(id);

        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.DELETED);
        verify(tenantRepository).save(tenant);
    }
}
