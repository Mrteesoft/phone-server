package com.phoneserver.controlplane.repository;

import java.util.Optional;
import java.util.UUID;

import com.phoneserver.controlplane.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByIdAndUser_Id(UUID id, UUID userId);

    @EntityGraph(attributePaths = "user")
    Optional<Device> findByDeviceToken(String deviceToken);
}
