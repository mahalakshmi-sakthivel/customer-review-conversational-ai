package com.customerreview.service;

import com.customerreview.dto.CustomerCreateRequest;
import com.customerreview.dto.CustomerResponse;
import com.customerreview.entity.Customer;
import com.customerreview.exception.ConflictException;
import com.customerreview.exception.ResourceNotFoundException;
import com.customerreview.repository.CustomerRepository;
import com.customerreview.security.EncryptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    public CustomerService(CustomerRepository customerRepository, EncryptionService encryptionService, AuditLogService auditLogService) {
        this.customerRepository = customerRepository;
        this.encryptionService = encryptionService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CustomerResponse createCustomer(UUID tenantId, CustomerCreateRequest request) {
        String phoneHash = encryptionService.hmacSha256(request.phone());
        if (customerRepository.existsByTenantIdAndPhoneHash(tenantId, phoneHash)) {
            throw new ConflictException("Customer with this phone number already exists in this tenant");
        }

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName(request.name());
        customer.setPhoneEncrypted(encryptionService.encrypt(request.phone()));
        customer.setPhoneHash(phoneHash);
        if (request.email() != null && !request.email().isBlank()) {
            customer.setEmailEncrypted(encryptionService.encrypt(request.email().trim()));
        }
        customer.setOptedOut(Boolean.TRUE.equals(request.optedOut()));
        customer.setPurchaseInfo(request.purchaseInfo());

        Customer saved = customerRepository.save(customer);
        auditLogService.logEvent(tenantId, null, "CUSTOMER_CREATED", "SUCCESS", "Customer created with ID: " + saved.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID tenantId, UUID customerId) {
        Customer customer = customerRepository.findByTenantIdAndId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId + " in tenant: " + tenantId));
        return mapToResponse(customer);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomers(UUID tenantId, Pageable pageable) {
        return customerRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public CustomerResponse updateOptOut(UUID tenantId, UUID customerId, boolean optedOut) {
        Customer customer = customerRepository.findByTenantIdAndId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        customer.setOptedOut(optedOut);
        Customer saved = customerRepository.save(customer);
        auditLogService.logEvent(tenantId, null, "CUSTOMER_OPT_OUT_CHANGED", "SUCCESS", "Opt-out set to: " + optedOut);
        return mapToResponse(saved);
    }

    public CustomerResponse mapToResponse(Customer c) {
        String decryptedPhone = encryptionService.decrypt(c.getPhoneEncrypted());
        String maskedPhone = maskPhone(decryptedPhone);
        String decryptedEmail = encryptionService.decrypt(c.getEmailEncrypted());

        return new CustomerResponse(
                c.getId(),
                c.getTenantId(),
                c.getName(),
                maskedPhone,
                c.getPhoneHash(),
                decryptedEmail,
                c.isOptedOut(),
                c.getPurchaseInfo(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, Math.min(3, phone.length())) + "****" + phone.substring(Math.max(phone.length() - 2, 0));
    }
}
