package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.TerminalCommand;

public record AgentTerminalCommandResponse(
        UUID commandId,
        UUID sessionId,
        Long sequenceNumber,
        String command,
        Integer timeoutSeconds,
        Instant createdAt
) {

    public static AgentTerminalCommandResponse fromEntity(TerminalCommand command) {
        return new AgentTerminalCommandResponse(
                command.getId(),
                command.getSession().getId(),
                command.getSequenceNumber(),
                command.getCommandText(),
                command.getTimeoutSeconds(),
                command.getCreatedAt()
        );
    }
}
