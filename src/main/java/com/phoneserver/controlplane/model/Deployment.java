package com.phoneserver.controlplane.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import com.phoneserver.controlplane.model.enums.DeploymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentStatus status;

    @Column(name = "public_url", length = 500)
    private String publicUrl;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "logs_pointer", length = 500)
    private String logsPointer;

    @Column(name = "last_reported_at")
    private Instant lastReportedAt;

    @OneToOne(mappedBy = "deployment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DomainMapping domainMapping;

    @PrePersist
    public void prePersist() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (status == null) {
            status = DeploymentStatus.STARTING;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(DeploymentStatus status) {
        this.status = status;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    public String getLogsPointer() {
        return logsPointer;
    }

    public void setLogsPointer(String logsPointer) {
        this.logsPointer = logsPointer;
    }

    public Instant getLastReportedAt() {
        return lastReportedAt;
    }

    public void setLastReportedAt(Instant lastReportedAt) {
        this.lastReportedAt = lastReportedAt;
    }

    public DomainMapping getDomainMapping() {
        return domainMapping;
    }

    public void setDomainMapping(DomainMapping domainMapping) {
        this.domainMapping = domainMapping;
        if (domainMapping != null && domainMapping.getDeployment() != this) {
            domainMapping.setDeployment(this);
        }
    }
}
