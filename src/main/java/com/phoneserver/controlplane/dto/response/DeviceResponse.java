package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.Device;
import com.phoneserver.controlplane.model.enums.DeviceStatus;

public record DeviceResponse(
        UUID id,
        UUID userId,
        String deviceName,
        DeviceStatus status,
        Instant lastSeenAt,
        Instant createdAt
) {

    public static DeviceResponse fromEntity(Device device, DeviceStatus effectiveStatus) {
        return new DeviceResponse(
                device.getId(),
                device.getUser().getId(),
                device.getDeviceName(),
                effectiveStatus,
                device.getLastSeenAt(),
                device.getCreatedAt()
        );
    }
}

