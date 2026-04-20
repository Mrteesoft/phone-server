package com.phoneserver.controlplane.dto.request;

import java.util.UUID;

import com.phoneserver.controlplane.model.enums.ProxyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeploymentStartRequest(
        @NotNull
        UUID projectId,

        @Size(max = 63)
        String subdomain,

        @Size(max = 255)
        String baseDomain,

        @Size(max = 120)
        String pathPrefix,

        ProxyType proxyType,

        @Size(max = 500)
        String publicUrl,

        @Size(max = 500)
        String logsPointer
) {
}
