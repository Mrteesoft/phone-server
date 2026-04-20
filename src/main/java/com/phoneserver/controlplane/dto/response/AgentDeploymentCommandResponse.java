package com.phoneserver.controlplane.dto.response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.phoneserver.controlplane.model.Deployment;
import com.phoneserver.controlplane.model.Project;
import com.phoneserver.controlplane.model.enums.DeploymentStatus;

public record AgentDeploymentCommandResponse(
        UUID deploymentId,
        UUID projectId,
        UUID deviceId,
        DeploymentStatus deploymentStatus,
        String runtime,
        String frameworkType,
        String repoUrl,
        String branch,
        String installCommand,
        String buildCommand,
        String startCommand,
        String outputDirectory,
        Integer localPort,
        Map<String, String> envConfig,
        String publicUrl,
        DomainMappingResponse domainMapping
) {

    public static AgentDeploymentCommandResponse fromEntity(Deployment deployment) {
        Project project = deployment.getProject();
        Map<String, String> envConfig = project.getEnvConfig() == null
                ? Map.of()
                : new LinkedHashMap<>(project.getEnvConfig());

        return new AgentDeploymentCommandResponse(
                deployment.getId(),
                project.getId(),
                project.getDevice().getId(),
                deployment.getStatus(),
                project.getRuntime(),
                project.getFrameworkType(),
                project.getRepoUrl(),
                project.getBranch(),
                project.getInstallCommand(),
                project.getBuildCommand(),
                project.getStartCommand(),
                project.getOutputDirectory(),
                project.getLocalPort(),
                envConfig,
                deployment.getPublicUrl(),
                deployment.getDomainMapping() == null ? null : DomainMappingResponse.fromEntity(deployment.getDomainMapping())
        );
    }
}
