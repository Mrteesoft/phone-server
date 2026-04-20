package com.phoneserver.controlplane.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.AgentTerminalCommandStatusReportRequest;
import com.phoneserver.controlplane.dto.request.AgentTerminalSessionStatusReportRequest;
import com.phoneserver.controlplane.dto.response.AgentTerminalCommandResponse;
import com.phoneserver.controlplane.dto.response.AgentTerminalSessionResponse;
import com.phoneserver.controlplane.dto.response.TerminalCommandResponse;
import com.phoneserver.controlplane.dto.response.TerminalSessionResponse;
import com.phoneserver.controlplane.exception.BadRequestException;
import com.phoneserver.controlplane.exception.ResourceNotFoundException;
import com.phoneserver.controlplane.model.Device;
import com.phoneserver.controlplane.model.TerminalCommand;
import com.phoneserver.controlplane.model.TerminalSession;
import com.phoneserver.controlplane.model.enums.DeviceStatus;
import com.phoneserver.controlplane.model.enums.TerminalCommandStatus;
import com.phoneserver.controlplane.model.enums.TerminalSessionStatus;
import com.phoneserver.controlplane.repository.DeviceRepository;
import com.phoneserver.controlplane.repository.TerminalCommandRepository;
import com.phoneserver.controlplane.repository.TerminalSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTerminalService {

    private final TerminalSessionRepository terminalSessionRepository;
    private final TerminalCommandRepository terminalCommandRepository;
    private final DeviceRepository deviceRepository;
    private final HeartbeatCacheService heartbeatCacheService;
    private final Clock clock;

    public AgentTerminalService(
            TerminalSessionRepository terminalSessionRepository,
            TerminalCommandRepository terminalCommandRepository,
            DeviceRepository deviceRepository,
            HeartbeatCacheService heartbeatCacheService,
            Clock clock
    ) {
        this.terminalSessionRepository = terminalSessionRepository;
        this.terminalCommandRepository = terminalCommandRepository;
        this.deviceRepository = deviceRepository;
        this.heartbeatCacheService = heartbeatCacheService;
        this.clock = clock;
    }

    @Transactional
    public List<AgentTerminalSessionResponse> getCurrentSessions(UUID deviceId) {
        touchDevice(deviceId);

        return terminalSessionRepository.findAllByDevice_IdAndStatusInOrderByCreatedAtDesc(
                        deviceId,
                        List.of(
                                TerminalSessionStatus.PENDING,
                                TerminalSessionStatus.ACTIVE,
                                TerminalSessionStatus.CLOSE_REQUESTED
                        )
                )
                .stream()
                .map(AgentTerminalSessionResponse::fromEntity)
                .toList();
    }

    @Transactional
    public List<AgentTerminalCommandResponse> getPendingCommands(UUID deviceId, UUID sessionId) {
        touchDevice(deviceId);
        findOwnedSession(deviceId, sessionId);

        return terminalCommandRepository.findAllBySession_IdAndStatusInOrderBySequenceNumberAsc(
                        sessionId,
                        List.of(TerminalCommandStatus.QUEUED)
                )
                .stream()
                .map(AgentTerminalCommandResponse::fromEntity)
                .toList();
    }

    @Transactional
    public TerminalSessionResponse reportSessionStatus(
            UUID deviceId,
            UUID sessionId,
            AgentTerminalSessionStatusReportRequest request
    ) {
        touchDevice(deviceId);

        TerminalSession session = findOwnedSession(deviceId, sessionId);
        TerminalSessionStatus currentStatus = session.getStatus();
        TerminalSessionStatus nextStatus = request.status();
        Instant now = clock.instant();

        if (isFinalStatus(currentStatus) && currentStatus != nextStatus) {
            throw new BadRequestException("Terminal session is already in a final state.");
        }
        if (currentStatus == TerminalSessionStatus.CLOSE_REQUESTED && nextStatus == TerminalSessionStatus.ACTIVE) {
            throw new BadRequestException("Terminal session is closing and cannot be reactivated.");
        }

        session.setStatus(nextStatus);
        session.setLastActivityAt(now);

        if (request.currentDirectory() != null) {
            session.setCurrentDirectory(trimToNull(request.currentDirectory()));
        }
        if (nextStatus == TerminalSessionStatus.ACTIVE && session.getOpenedAt() == null) {
            session.setOpenedAt(now);
        }
        if (isFinalStatus(nextStatus) && session.getClosedAt() == null) {
            session.setClosedAt(now);
            cancelQueuedCommands(sessionId, now);
        }

        TerminalSession savedSession = terminalSessionRepository.save(session);
        return TerminalSessionResponse.fromEntity(savedSession);
    }

    @Transactional
    public TerminalCommandResponse reportCommandStatus(
            UUID deviceId,
            UUID commandId,
            AgentTerminalCommandStatusReportRequest request
    ) {
        touchDevice(deviceId);

        TerminalCommand command = terminalCommandRepository.findByIdAndSession_Device_Id(commandId, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal command not found for device."));
        TerminalSession session = command.getSession();
        TerminalCommandStatus currentStatus = command.getStatus();
        TerminalCommandStatus nextStatus = request.status();
        Instant now = clock.instant();

        if (isFinalStatus(currentStatus) && currentStatus != nextStatus) {
            throw new BadRequestException("Terminal command is already in a final state.");
        }

        command.setStatus(nextStatus);
        if (request.outputText() != null) {
            command.setOutputText(request.outputText());
        }
        if (request.workingDirectory() != null) {
            String normalizedDirectory = trimToNull(request.workingDirectory());
            command.setWorkingDirectory(normalizedDirectory);
            session.setCurrentDirectory(normalizedDirectory);
        }
        if (request.exitCode() != null) {
            command.setExitCode(request.exitCode());
        }
        if (nextStatus == TerminalCommandStatus.RUNNING && command.getStartedAt() == null) {
            command.setStartedAt(now);
        }
        if (isFinalStatus(nextStatus) && command.getCompletedAt() == null) {
            command.setCompletedAt(now);
        }

        session.setLastActivityAt(now);
        if (session.getStatus() == TerminalSessionStatus.PENDING) {
            session.setStatus(TerminalSessionStatus.ACTIVE);
            if (session.getOpenedAt() == null) {
                session.setOpenedAt(now);
            }
        }

        terminalSessionRepository.save(session);
        TerminalCommand savedCommand = terminalCommandRepository.save(command);
        return TerminalCommandResponse.fromEntity(savedCommand);
    }

    private TerminalSession findOwnedSession(UUID deviceId, UUID sessionId) {
        return terminalSessionRepository.findByIdAndDevice_Id(sessionId, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal session not found for device."));
    }

    private void touchDevice(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found."));
        Instant now = clock.instant();

        device.setStatus(DeviceStatus.ONLINE);
        device.setLastSeenAt(now);
        deviceRepository.save(device);
        heartbeatCacheService.recordHeartbeat(deviceId, now);
    }

    private boolean isFinalStatus(TerminalSessionStatus status) {
        return status == TerminalSessionStatus.CLOSED || status == TerminalSessionStatus.FAILED;
    }

    private boolean isFinalStatus(TerminalCommandStatus status) {
        return status == TerminalCommandStatus.SUCCEEDED
                || status == TerminalCommandStatus.FAILED
                || status == TerminalCommandStatus.CANCELLED;
    }

    private void cancelQueuedCommands(UUID sessionId, Instant now) {
        List<TerminalCommand> queuedCommands =
                terminalCommandRepository.findAllBySession_IdAndStatusInOrderBySequenceNumberAsc(
                        sessionId,
                        List.of(TerminalCommandStatus.QUEUED)
                );

        for (TerminalCommand command : queuedCommands) {
            command.setStatus(TerminalCommandStatus.CANCELLED);
            command.setCompletedAt(now);
        }

        if (!queuedCommands.isEmpty()) {
            terminalCommandRepository.saveAll(queuedCommands);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
