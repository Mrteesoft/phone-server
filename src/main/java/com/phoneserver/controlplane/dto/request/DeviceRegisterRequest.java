package com.phoneserver.controlplane.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceRegisterRequest(
        @NotBlank
        @Size(max = 100)
        String deviceName
) {
}

