package com.phoneserver.controlplane.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.phoneserver.controlplane.model.Device;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class DeviceAgentPrincipal {

    private final UUID deviceId;
    private final UUID userId;
    private final String deviceName;
    private final String deviceToken;

    public DeviceAgentPrincipal(UUID deviceId, UUID userId, String deviceName, String deviceToken) {
        this.deviceId = deviceId;
        this.userId = userId;
        this.deviceName = deviceName;
        this.deviceToken = deviceToken;
    }

    public static DeviceAgentPrincipal fromEntity(Device device) {
        return new DeviceAgentPrincipal(
                device.getId(),
                device.getUser().getId(),
                device.getDeviceName(),
                device.getDeviceToken()
        );
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_DEVICE_AGENT"));
    }
}

