package com.phoneserver.controlplane.dto.request;

import com.phoneserver.controlplane.model.enums.DeploymentStatus;
import com.phoneserver.controlplane.model.enums.DomainMappingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentDeploymentStatusReportRequest(
        @NotNull
        DeploymentStatus status,

        DomainMappingStatus domainStatus,

        @Size(max = 500)
        String publicUrl,

        @Size(max = 500)
        String logsPointer
) {
}
