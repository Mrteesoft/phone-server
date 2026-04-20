package com.phoneserver.controlplane.dto.request;

import com.phoneserver.controlplane.model.enums.TerminalSessionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentTerminalSessionStatusReportRequest(
        @NotNull
        TerminalSessionStatus status,

        @Size(max = 500)
        String currentDirectory
) {
}
