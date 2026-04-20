package com.phoneserver.controlplane.dto.request;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
        @NotNull
        UUID deviceId,

        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Size(max = 50)
        String runtime,

        @NotBlank
        @Size(max = 50)
        String frameworkType,

        @NotBlank
        @Size(max = 500)
        String repoUrl,

        @Size(max = 120)
        String branch,

        @Size(max = 500)
        String installCommand,

        @Size(max = 500)
        String buildCommand,

        @NotBlank
        @Size(max = 500)
        String startCommand,

        @Size(max = 255)
        String outputDirectory,

        @NotNull
        @Min(1)
        @Max(65535)
        Integer localPort,

        Map<String, String> envConfig
) {
}
