package com.phoneserver.controlplane.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.phoneserver.controlplane.model.Deployment;
import com.phoneserver.controlplane.model.enums.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    Optional<Deployment> findByIdAndProject_User_Id(UUID id, UUID userId);

    Optional<Deployment> findByIdAndProject_Device_Id(UUID id, UUID deviceId);

    Optional<Deployment> findFirstByProject_IdAndStatusInOrderByStartedAtDesc(
            UUID projectId,
            Collection<DeploymentStatus> statuses
    );

    List<Deployment> findAllByProject_Device_IdAndStatusInOrderByStartedAtDesc(
            UUID deviceId,
            Collection<DeploymentStatus> statuses
    );
}
