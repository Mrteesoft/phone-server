package com.phoneserver.controlplane.model;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.enums.TerminalCommandStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "terminal_commands")
public class TerminalCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TerminalSession session;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "command_text", nullable = false, columnDefinition = "TEXT")
    private String commandText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TerminalCommandStatus status;

    @Column(name = "output_text", columnDefinition = "TEXT")
    private String outputText;

    @Column(name = "working_directory", length = 500)
    private String workingDirectory;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = TerminalCommandStatus.QUEUED;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public TerminalSession getSession() {
        return session;
    }

    public void setSession(TerminalSession session) {
        this.session = session;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getCommandText() {
        return commandText;
    }

    public void setCommandText(String commandText) {
        this.commandText = commandText;
    }

    public TerminalCommandStatus getStatus() {
        return status;
    }

    public void setStatus(TerminalCommandStatus status) {
        this.status = status;
    }

    public String getOutputText() {
        return outputText;
    }

    public void setOutputText(String outputText) {
        this.outputText = outputText;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
