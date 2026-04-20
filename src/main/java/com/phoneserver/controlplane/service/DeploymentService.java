package com.phoneserver.controlplane.service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.DeploymentStartRequest;
import com.phoneserver.controlplane.dto.request.DeploymentStopRequest;
import com.phoneserver.controlplane.dto.response.DeploymentResponse;
import com.phoneserver.controlplane.exception.BadRequestException;
import com.phoneserver.controlplane.exception.ResourceNotFoundException;
import com.phoneserver.controlplane.model.Deployment;
import com.phoneserver.controlplane.model.DomainMapping;
import com.phoneserver.controlplane.model.Project;
import com.phoneserver.controlplane.model.enums.DeploymentStatus;
import com.phoneserver.controlplane.model.enums.ProxyType;
import com.phoneserver.controlplane.repository.DeploymentRepository;
import com.phoneserver.controlplane.repository.DomainMappingRepository;
import com.phoneserver.controlplane.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final DomainMappingRepository domainMappingRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            DomainMappingRepository domainMappingRepository,
            ProjectRepository projectRepository,
            Clock clock
    ) {
        this.deploymentRepository = deploymentRepository;
        this.domainMappingRepository = domainMappingRepository;
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    @Transactional
    public DeploymentResponse startDeployment(UUID userId, DeploymentStartRequest request) {
        Project project = projectRepository.findByIdAndUser_Id(request.projectId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found."));

        deploymentRepository.findFirstByProject_IdAndStatusInOrderByStartedAtDesc(
                        project.getId(),
                        List.of(
                                DeploymentStatus.STARTING,
                                DeploymentStatus.BUILDING,
                                DeploymentStatus.RUNNING,
                                DeploymentStatus.STOP_REQUESTED
                        )
                )
                .ifPresent(existing -> {
                    throw new BadRequestException("This project already has an active deployment.");
                });

        Deployment deployment = new Deployment();
        deployment.setProject(project);
        deployment.setStatus(DeploymentStatus.STARTING);
        deployment.setLogsPointer(trimToNull(request.logsPointer()));
        deployment.setStartedAt(clock.instant());

        DomainMapping domainMapping = maybeCreateDomainMapping(project, request);
        if (domainMapping != null) {
            deployment.setDomainMapping(domainMapping);
            deployment.setPublicUrl(buildPublicUrl(domainMapping));
        } else {
            deployment.setPublicUrl(trimToNull(request.publicUrl()));
        }

        Deployment savedDeployment = deploymentRepository.save(deployment);
        return DeploymentResponse.fromEntity(savedDeployment);
    }

    @Transactional
    public DeploymentResponse stopDeployment(UUID userId, DeploymentStopRequest request) {
        Deployment deployment = deploymentRepository.findByIdAndProject_User_Id(request.deploymentId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found."));

        if (deployment.getStatus() == DeploymentStatus.STOPPED || deployment.getStatus() == DeploymentStatus.FAILED) {
            throw new BadRequestException("This deployment is already in a terminal state.");
        }

        DeploymentStatus requestedStatus = request.status();
        if (requestedStatus != null
                && requestedStatus != DeploymentStatus.STOPPED
                && requestedStatus != DeploymentStatus.STOP_REQUESTED) {
            throw new BadRequestException("Deployment stop status must be STOPPED or STOP_REQUESTED.");
        }

        deployment.setStatus(DeploymentStatus.STOP_REQUESTED);
        if (request.logsPointer() != null) {
            deployment.setLogsPointer(trimToNull(request.logsPointer()));
        }

        Deployment savedDeployment = deploymentRepository.save(deployment);
        return DeploymentResponse.fromEntity(savedDeployment);
    }

    @Transactional(readOnly = true)
    public DeploymentResponse getDeployment(UUID userId, UUID deploymentId) {
        Deployment deployment = deploymentRepository.findByIdAndProject_User_Id(deploymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found."));

        return DeploymentResponse.fromEntity(deployment);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DomainMapping maybeCreateDomainMapping(Project project, DeploymentStartRequest request) {
        String subdomain = normalizeSubdomain(request.subdomain());
        String baseDomain = normalizeDomainComponent(request.baseDomain());

        if (subdomain == null && baseDomain == null) {
            return null;
        }

        if (subdomain == null || baseDomain == null) {
            throw new BadRequestException("Both subdomain and baseDomain are required when assigning a domain.");
        }

        String fullDomain = subdomain + "." + baseDomain;
        if (domainMappingRepository.existsByFullDomainIgnoreCase(fullDomain)) {
            throw new BadRequestException("That public domain is already assigned to another deployment.");
        }

        DomainMapping domainMapping = new DomainMapping();
        domainMapping.setFullDomain(fullDomain);
        domainMapping.setBaseDomain(baseDomain);
        domainMapping.setSubdomain(subdomain);
        domainMapping.setPathPrefix(normalizePathPrefix(request.pathPrefix()));
        domainMapping.setProxyType(request.proxyType() == null ? ProxyType.NGINX : request.proxyType());
        domainMapping.setTargetPort(project.getLocalPort());
        domainMapping.setStatus(DomainMappingStatus.PENDING);
        return domainMapping;
    }

    private String normalizeSubdomain(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeDomainComponent(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePathPrefix(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private String buildPublicUrl(DomainMapping domainMapping) {
        String pathPrefix = domainMapping.getPathPrefix();
        return pathPrefix == null
                ? "https://" + domainMapping.getFullDomain()
                : "https://" + domainMapping.getFullDomain() + pathPrefix;
    }
}
