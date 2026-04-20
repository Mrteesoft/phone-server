package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.TerminalSession;
import com.phoneserver.controlplane.model.enums.TerminalSessionStatus;

public record AgentTerminalSessionResponse(
        UUID sessionId,
        String displayName,
        TerminalSessionStatus status,
        String shellType,
        String currentDirectory,
        Instant createdAt,
        Instant openedAt,
        Instant closedAt,
        Instant lastActivityAt
) {

    public static AgentTerminalSessionResponse fromEntity(TerminalSession session) {
        return new AgentTerminalSessionResponse(
                session.getId(),
                session.getDisplayName(),
                session.getStatus(),
                session.getShellType(),
                session.getCurrentDirectory(),
                session.getCreatedAt(),
                session.getOpenedAt(),
                session.getClosedAt(),
                session.getLastActivityAt()
        );
    }
}
