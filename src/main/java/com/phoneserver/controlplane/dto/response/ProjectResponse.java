package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.phoneserver.controlplane.model.Project;

public record ProjectResponse(
        UUID id,
        UUID userId,
        UUID deviceId,
        String name,
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
        Instant createdAt
) {

    public static ProjectResponse fromEntity(Project project) {
        Map<String, String> envConfig = project.getEnvConfig() == null
                ? Map.of()
                : new LinkedHashMap<>(project.getEnvConfig());

        return new ProjectResponse(
                project.getId(),
                project.getUser().getId(),
                project.getDevice().getId(),
                project.getName(),
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
                project.getCreatedAt()
        );
    }
}
