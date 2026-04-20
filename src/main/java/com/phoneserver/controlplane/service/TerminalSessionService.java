package com.phoneserver.controlplane.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.TerminalCommandCreateRequest;
import com.phoneserver.controlplane.dto.request.TerminalSessionCreateRequest;
import com.phoneserver.controlplane.dto.response.TerminalCommandResponse;
import com.phoneserver.controlplane.dto.response.TerminalSessionResponse;
import com.phoneserver.controlplane.exception.BadRequestException;
import com.phoneserver.controlplane.exception.ResourceNotFoundException;
import com.phoneserver.controlplane.model.Device;
import com.phoneserver.controlplane.model.TerminalCommand;
import com.phoneserver.controlplane.model.TerminalSession;
import com.phoneserver.controlplane.model.User;
import com.phoneserver.controlplane.model.enums.TerminalCommandStatus;
import com.phoneserver.controlplane.model.enums.TerminalSessionStatus;
import com.phoneserver.controlplane.repository.DeviceRepository;
import com.phoneserver.controlplane.repository.TerminalCommandRepository;
import com.phoneserver.controlplane.repository.TerminalSessionRepository;
import com.phoneserver.controlplane.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminalSessionService {

    private final TerminalSessionRepository terminalSessionRepository;
    private final TerminalCommandRepository terminalCommandRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TerminalSessionService(
            TerminalSessionRepository terminalSessionRepository,
            TerminalCommandRepository terminalCommandRepository,
            DeviceRepository deviceRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.terminalSessionRepository = terminalSessionRepository;
        this.terminalCommandRepository = terminalCommandRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public TerminalSessionResponse createSession(UUID userId, TerminalSessionCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        Device device = deviceRepository.findByIdAndUser_Id(request.deviceId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found."));

        TerminalSession session = new TerminalSession();
        session.setUser(user);
        session.setDevice(device);
        session.setDisplayName(normalizeOptional(request.displayName(), device.getDeviceName() + " terminal"));
        session.setShellType(normalizeShellType(request.shellType()));
        session.setStatus(TerminalSessionStatus.PENDING);
        session.setLastActivityAt(clock.instant());

        TerminalSession savedSession = terminalSessionRepository.save(session);
        return TerminalSessionResponse.fromEntity(savedSession);
    }

    @Transactional(readOnly = true)
    public List<TerminalSessionResponse> listSessions(UUID userId, UUID deviceId) {
        List<TerminalSession> sessions = deviceId == null
                ? terminalSessionRepository.findAllByUser_IdOrderByCreatedAtDesc(userId)
                : terminalSessionRepository.findAllByUser_IdAndDevice_IdOrderByCreatedAtDesc(userId, deviceId);

        return sessions.stream()
                .map(TerminalSessionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public TerminalSessionResponse getSession(UUID userId, UUID sessionId) {
        TerminalSession session = findOwnedSession(sessionId, userId);
        return TerminalSessionResponse.fromEntity(session);
    }

    @Transactional(readOnly = true)
    public List<TerminalCommandResponse> listCommands(UUID userId, UUID sessionId) {
        findOwnedSession(sessionId, userId);

        return terminalCommandRepository.findAllBySession_IdAndSession_User_IdOrderBySequenceNumberAsc(sessionId, userId)
                .stream()
                .map(TerminalCommandResponse::fromEntity)
                .toList();
    }

    @Transactional
    public TerminalCommandResponse createCommand(UUID userId, UUID sessionId, TerminalCommandCreateRequest request) {
        TerminalSession session = findOwnedSession(sessionId, userId);
        validateSessionAcceptsCommands(session);

        TerminalCommand command = new TerminalCommand();
        command.setSession(session);
        command.setSequenceNumber(nextSequenceNumber(sessionId));
        command.setCommandText(request.command().trim());
        command.setTimeoutSeconds(request.timeoutSeconds());

        Instant now = clock.instant();
        session.setLastActivityAt(now);

        TerminalCommand savedCommand = terminalCommandRepository.save(command);
        return TerminalCommandResponse.fromEntity(savedCommand);
    }

    @Transactional
    public TerminalSessionResponse closeSession(UUID userId, UUID sessionId) {
        TerminalSession session = findOwnedSession(sessionId, userId);
        Instant now = clock.instant();

        if (session.getStatus() == TerminalSessionStatus.CLOSED || session.getStatus() == TerminalSessionStatus.FAILED) {
            throw new BadRequestException("Terminal session is already closed.");
        }

        if (session.getStatus() != TerminalSessionStatus.CLOSE_REQUESTED) {
            session.setStatus(TerminalSessionStatus.CLOSE_REQUESTED);
            session.setLastActivityAt(now);
            cancelQueuedCommands(sessionId, now);
        }

        TerminalSession savedSession = terminalSessionRepository.save(session);
        return TerminalSessionResponse.fromEntity(savedSession);
    }

    private TerminalSession findOwnedSession(UUID sessionId, UUID userId) {
        return terminalSessionRepository.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal session not found."));
    }

    private void validateSessionAcceptsCommands(TerminalSession session) {
        if (session.getStatus() == TerminalSessionStatus.CLOSE_REQUESTED) {
            throw new BadRequestException("Terminal session is closing and cannot accept new commands.");
        }
        if (session.getStatus() == TerminalSessionStatus.CLOSED || session.getStatus() == TerminalSessionStatus.FAILED) {
            throw new BadRequestException("Terminal session is closed and cannot accept new commands.");
        }
    }

    private long nextSequenceNumber(UUID sessionId) {
        return terminalCommandRepository.findTopBySession_IdOrderBySequenceNumberDesc(sessionId)
                .map(command -> command.getSequenceNumber() + 1)
                .orElse(1L);
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

    private String normalizeOptional(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String normalizeShellType(String value) {
        String normalized = normalizeOptional(value, "sh");
        return normalized.toLowerCase(Locale.ROOT);
    }
}
