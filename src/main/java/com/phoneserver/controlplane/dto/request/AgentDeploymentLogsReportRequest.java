package com.phoneserver.controlplane.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentDeploymentLogsReportRequest(
        @NotBlank
        @Size(max = 500)
        String logsPointer
) {
}

