package com.phoneserver.controlplane.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TerminalCommandCreateRequest(
        @NotBlank
        @Size(max = 4000)
        String command,

        @Min(1)
        @Max(3600)
        Integer timeoutSeconds
) {
}
