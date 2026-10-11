package com.customerreview.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 150)
    private String name;

    @Column(name = "phone_encrypted", nullable = false)
    private byte[] phoneEncrypted;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String phoneHash;

    @Column(name = "email_encrypted")
    private byte[] emailEncrypted;

    @Column(name = "opted_out", nullable = false)
    private boolean optedOut = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "purchase_info", columnDefinition = "jsonb")
    private String purchaseInfo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Customer() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getPhoneEncrypted() {
        return phoneEncrypted;
    }

    public void setPhoneEncrypted(byte[] phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public byte[] getEmailEncrypted() {
        return emailEncrypted;
    }

    public void setEmailEncrypted(byte[] emailEncrypted) {
        this.emailEncrypted = emailEncrypted;
    }

    public boolean isOptedOut() {
        return optedOut;
    }

    public void setOptedOut(boolean optedOut) {
        this.optedOut = optedOut;
    }

    public String getPurchaseInfo() {
        return purchaseInfo;
    }

    public void setPurchaseInfo(String purchaseInfo) {
        this.purchaseInfo = purchaseInfo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
