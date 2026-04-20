package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.TerminalCommand;
import com.phoneserver.controlplane.model.enums.TerminalCommandStatus;

public record TerminalCommandResponse(
        UUID id,
        UUID sessionId,
        Long sequenceNumber,
        String command,
        TerminalCommandStatus status,
        String outputText,
        String workingDirectory,
        Integer exitCode,
        Integer timeoutSeconds,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {

    public static TerminalCommandResponse fromEntity(TerminalCommand command) {
        return new TerminalCommandResponse(
                command.getId(),
                command.getSession().getId(),
                command.getSequenceNumber(),
                command.getCommandText(),
                command.getStatus(),
                command.getOutputText(),
                command.getWorkingDirectory(),
                command.getExitCode(),
                command.getTimeoutSeconds(),
                command.getCreatedAt(),
                command.getStartedAt(),
                command.getCompletedAt()
        );
    }
}
