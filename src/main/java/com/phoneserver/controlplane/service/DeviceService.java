package com.phoneserver.controlplane.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.DeviceHeartbeatRequest;
import com.phoneserver.controlplane.dto.request.DeviceRegisterRequest;
import com.phoneserver.controlplane.dto.response.DeviceRegistrationResponse;
import com.phoneserver.controlplane.dto.response.DeviceResponse;
import com.phoneserver.controlplane.exception.ResourceNotFoundException;
import com.phoneserver.controlplane.model.Device;
import com.phoneserver.controlplane.model.User;
import com.phoneserver.controlplane.model.enums.DeviceStatus;
import com.phoneserver.controlplane.repository.DeviceRepository;
import com.phoneserver.controlplane.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final HeartbeatCacheService heartbeatCacheService;
    private final Clock clock;

    public DeviceService(
            DeviceRepository deviceRepository,
            UserRepository userRepository,
            HeartbeatCacheService heartbeatCacheService,
            Clock clock
    ) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.heartbeatCacheService = heartbeatCacheService;
        this.clock = clock;
    }

    @Transactional
    public DeviceRegistrationResponse register(UUID userId, DeviceRegisterRequest request) {
        User user = findUser(userId);

        Device device = new Device();
        device.setUser(user);
        device.setDeviceName(request.deviceName().trim());
        device.setDeviceToken(generateUniqueDeviceToken());
        device.setStatus(DeviceStatus.REGISTERED);

        Device savedDevice = deviceRepository.save(device);
        return DeviceRegistrationResponse.fromEntity(savedDevice, resolveDeviceStatus(savedDevice));
    }

    @Transactional
    public DeviceResponse heartbeat(UUID userId, DeviceHeartbeatRequest request) {
        Device device = findOwnedDevice(request.deviceId(), userId);
        Instant now = clock.instant();

        device.setLastSeenAt(now);
        device.setStatus(DeviceStatus.ONLINE);

        Device savedDevice = deviceRepository.save(device);
        heartbeatCacheService.recordHeartbeat(savedDevice.getId(), now);

        return DeviceResponse.fromEntity(savedDevice, resolveDeviceStatus(savedDevice));
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDevice(UUID userId, UUID deviceId) {
        Device device = findOwnedDevice(deviceId, userId);
        return DeviceResponse.fromEntity(device, resolveDeviceStatus(device));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private Device findOwnedDevice(UUID deviceId, UUID userId) {
        return deviceRepository.findByIdAndUser_Id(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found."));
    }

    private DeviceStatus resolveDeviceStatus(Device device) {
        if (heartbeatCacheService.hasRecentHeartbeat(device.getId())) {
            return DeviceStatus.ONLINE;
        }

        if (device.getLastSeenAt() == null) {
            return DeviceStatus.REGISTERED;
        }

        return DeviceStatus.OFFLINE;
    }

    private String generateUniqueDeviceToken() {
        String token;

        do {
            byte[] bytes = new byte[32];
            SECURE_RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (deviceRepository.findByDeviceToken(token).isPresent());

        return token;
    }
}
