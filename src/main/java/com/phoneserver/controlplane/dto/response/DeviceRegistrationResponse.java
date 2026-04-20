package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.Device;
import com.phoneserver.controlplane.model.enums.DeviceStatus;

public record DeviceRegistrationResponse(
        UUID id,
        UUID userId,
        String deviceName,
        String deviceToken,
        DeviceStatus status,
        Instant lastSeenAt,
        Instant createdAt
) {

    public static DeviceRegistrationResponse fromEntity(Device device, DeviceStatus effectiveStatus) {
        return new DeviceRegistrationResponse(
                device.getId(),
                device.getUser().getId(),
                device.getDeviceName(),
                device.getDeviceToken(),
                effectiveStatus,
                device.getLastSeenAt(),
                device.getCreatedAt()
        );
    }
}

