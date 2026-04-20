package com.phoneserver.controlplane.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TerminalSessionCreateRequest(
        @NotNull
        UUID deviceId,

        @Size(max = 120)
        String displayName,

        @Size(max = 50)
        String shellType
) {
}
