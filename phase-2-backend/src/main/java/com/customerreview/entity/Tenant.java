package com.customerreview.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "evolution_instance_name", unique = true, length = 100)
    private String evolutionInstanceName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Tenant() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

    public Tenant(UUID id, String name, String status, String evolutionInstanceName) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.status = status != null ? status : "ACTIVE";
        this.evolutionInstanceName = evolutionInstanceName;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEvolutionInstanceName() {
        return evolutionInstanceName;
    }

    public void setEvolutionInstanceName(String evolutionInstanceName) {
        this.evolutionInstanceName = evolutionInstanceName;
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
