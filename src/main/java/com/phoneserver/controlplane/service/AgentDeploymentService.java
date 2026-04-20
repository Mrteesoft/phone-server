package com.phoneserver.controlplane.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.AgentDeploymentLogsReportRequest;
import com.phoneserver.controlplane.dto.request.AgentDeploymentStatusReportRequest;
import com.phoneserver.controlplane.dto.response.AgentDeploymentCommandResponse;
import com.phoneserver.controlplane.dto.response.DeploymentResponse;
import com.phoneserver.controlplane.exception.ResourceNotFoundException;
import com.phoneserver.controlplane.model.Deployment;
import com.phoneserver.controlplane.model.Device;
import com.phoneserver.controlplane.model.DomainMapping;
import com.phoneserver.controlplane.model.enums.DeploymentStatus;
import com.phoneserver.controlplane.model.enums.DeviceStatus;
import com.phoneserver.controlplane.model.enums.DomainMappingStatus;
import com.phoneserver.controlplane.repository.DeploymentRepository;
import com.phoneserver.controlplane.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentDeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final DeviceRepository deviceRepository;
    private final HeartbeatCacheService heartbeatCacheService;
    private final Clock clock;

    public AgentDeploymentService(
            DeploymentRepository deploymentRepository,
            DeviceRepository deviceRepository,
            HeartbeatCacheService heartbeatCacheService,
            Clock clock
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deviceRepository = deviceRepository;
        this.heartbeatCacheService = heartbeatCacheService;
        this.clock = clock;
    }

    @Transactional
    public List<AgentDeploymentCommandResponse> getCurrentCommands(UUID deviceId) {
        touchDevice(deviceId);
        return deploymentRepository.findAllByProject_Device_IdAndStatusInOrderByStartedAtDesc(
                        deviceId,
                        List.of(
                                DeploymentStatus.STARTING,
                                DeploymentStatus.BUILDING,
                                DeploymentStatus.RUNNING,
                                DeploymentStatus.STOP_REQUESTED
                        )
                )
                .stream()
                .map(AgentDeploymentCommandResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AgentDeploymentCommandResponse getDeploymentCommand(UUID deviceId, UUID deploymentId) {
        touchDevice(deviceId);
        Deployment deployment = findOwnedDeployment(deviceId, deploymentId);
        return AgentDeploymentCommandResponse.fromEntity(deployment);
    }

    @Transactional
    public DeploymentResponse reportStatus(
            UUID deviceId,
            UUID deploymentId,
            AgentDeploymentStatusReportRequest request
    ) {
        touchDevice(deviceId);

        Deployment deployment = findOwnedDeployment(deviceId, deploymentId);
        Instant now = clock.instant();

        deployment.setStatus(request.status());
        deployment.setLastReportedAt(now);

        if (request.logsPointer() != null) {
            deployment.setLogsPointer(trimToNull(request.logsPointer()));
        }
        if (request.publicUrl() != null) {
            deployment.setPublicUrl(trimToNull(request.publicUrl()));
        }
        if (request.status() == DeploymentStatus.STOPPED || request.status() == DeploymentStatus.FAILED) {
            deployment.setStoppedAt(now);
        }

        updateDomainMappingStatus(deployment.getDomainMapping(), request);

        Deployment savedDeployment = deploymentRepository.save(deployment);
        return DeploymentResponse.fromEntity(savedDeployment);
    }

    @Transactional
    public DeploymentResponse reportLogs(
            UUID deviceId,
            UUID deploymentId,
            AgentDeploymentLogsReportRequest request
    ) {
        touchDevice(deviceId);

        Deployment deployment = findOwnedDeployment(deviceId, deploymentId);
        deployment.setLogsPointer(trimToNull(request.logsPointer()));
        deployment.setLastReportedAt(clock.instant());

        Deployment savedDeployment = deploymentRepository.save(deployment);
        return DeploymentResponse.fromEntity(savedDeployment);
    }

    private Deployment findOwnedDeployment(UUID deviceId, UUID deploymentId) {
        return deploymentRepository.findByIdAndProject_Device_Id(deploymentId, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found for device."));
    }

    private void touchDevice(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found."));
        Instant now = clock.instant();

        device.setStatus(DeviceStatus.ONLINE);
        device.setLastSeenAt(now);
        deviceRepository.save(device);
        heartbeatCacheService.recordHeartbeat(deviceId, now);
    }

    private void updateDomainMappingStatus(
            DomainMapping domainMapping,
            AgentDeploymentStatusReportRequest request
    ) {
        if (domainMapping == null) {
            return;
        }

        if (request.domainStatus() != null) {
            domainMapping.setStatus(request.domainStatus());
            return;
        }

        if (request.status() == DeploymentStatus.RUNNING) {
            domainMapping.setStatus(DomainMappingStatus.ACTIVE);
        } else if (request.status() == DeploymentStatus.FAILED) {
            domainMapping.setStatus(DomainMappingStatus.FAILED);
        } else if (request.status() == DeploymentStatus.STOPPED) {
            domainMapping.setStatus(DomainMappingStatus.RELEASED);
        } else {
            domainMapping.setStatus(DomainMappingStatus.PENDING);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
