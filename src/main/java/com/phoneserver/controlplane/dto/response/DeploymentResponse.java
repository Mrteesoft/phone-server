package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.Deployment;
import com.phoneserver.controlplane.model.enums.DeploymentStatus;

public record DeploymentResponse(
        UUID id,
        UUID projectId,
        DeploymentStatus status,
        String publicUrl,
        Instant startedAt,
        Instant stoppedAt,
        String logsPointer,
        Instant lastReportedAt,
        DomainMappingResponse domainMapping
) {

    public static DeploymentResponse fromEntity(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getProject().getId(),
                deployment.getStatus(),
                deployment.getPublicUrl(),
                deployment.getStartedAt(),
                deployment.getStoppedAt(),
                deployment.getLogsPointer(),
                deployment.getLastReportedAt(),
                deployment.getDomainMapping() == null ? null : DomainMappingResponse.fromEntity(deployment.getDomainMapping())
        );
    }
}
