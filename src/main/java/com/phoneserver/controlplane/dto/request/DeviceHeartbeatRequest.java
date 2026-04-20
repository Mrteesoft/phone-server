package com.phoneserver.controlplane.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record DeviceHeartbeatRequest(
        @NotNull
        UUID deviceId
) {
}

