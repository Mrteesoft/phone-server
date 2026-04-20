package com.phoneserver.controlplane.dto.request;

import com.phoneserver.controlplane.model.enums.TerminalCommandStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentTerminalCommandStatusReportRequest(
        @NotNull
        TerminalCommandStatus status,

        @Size(max = 100000)
        String outputText,

        @Size(max = 500)
        String workingDirectory,

        Integer exitCode
) {
}
