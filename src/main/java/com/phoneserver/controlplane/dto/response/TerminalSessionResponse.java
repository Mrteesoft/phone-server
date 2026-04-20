package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.TerminalSession;
import com.phoneserver.controlplane.model.enums.TerminalSessionStatus;

public record TerminalSessionResponse(
        UUID id,
        UUID userId,
        UUID deviceId,
        String deviceName,
        String displayName,
        TerminalSessionStatus status,
        String shellType,
        String currentDirectory,
        Instant createdAt,
        Instant openedAt,
        Instant closedAt,
        Instant lastActivityAt
) {

    public static TerminalSessionResponse fromEntity(TerminalSession session) {
        return new TerminalSessionResponse(
                session.getId(),
                session.getUser().getId(),
                session.getDevice().getId(),
                session.getDevice().getDeviceName(),
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
