package com.phoneserver.controlplane.dto.request;

import java.util.UUID;

import com.phoneserver.controlplane.model.enums.DeploymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeploymentStopRequest(
        @NotNull
        UUID deploymentId,

        DeploymentStatus status,

        @Size(max = 500)
        String logsPointer
) {
}
