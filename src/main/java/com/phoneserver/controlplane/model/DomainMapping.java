package com.phoneserver.controlplane.model;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.enums.DomainMappingStatus;
import com.phoneserver.controlplane.model.enums.ProxyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "domain_mappings")
public class DomainMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false, unique = true)
    private Deployment deployment;

    @Column(name = "full_domain", nullable = false, unique = true, length = 255)
    private String fullDomain;

    @Column(name = "base_domain", nullable = false, length = 255)
    private String baseDomain;

    @Column(nullable = false, length = 63)
    private String subdomain;

    @Column(name = "path_prefix", length = 120)
    private String pathPrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "proxy_type", nullable = false, length = 32)
    private ProxyType proxyType;

    @Column(name = "target_port", nullable = false)
    private Integer targetPort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DomainMappingStatus status;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @PrePersist
    public void prePersist() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
        if (status == null) {
            status = DomainMappingStatus.PENDING;
        }
        if (proxyType == null) {
            proxyType = ProxyType.NGINX;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public String getFullDomain() {
        return fullDomain;
    }

    public void setFullDomain(String fullDomain) {
        this.fullDomain = fullDomain;
    }

    public String getBaseDomain() {
        return baseDomain;
    }

    public void setBaseDomain(String baseDomain) {
        this.baseDomain = baseDomain;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public ProxyType getProxyType() {
        return proxyType;
    }

    public void setProxyType(ProxyType proxyType) {
        this.proxyType = proxyType;
    }

    public Integer getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    public DomainMappingStatus getStatus() {
        return status;
    }

    public void setStatus(DomainMappingStatus status) {
        this.status = status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}
