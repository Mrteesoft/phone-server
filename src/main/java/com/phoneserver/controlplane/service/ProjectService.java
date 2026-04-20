package com.phoneserver.controlplane.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.ProjectCreateRequest;
import com.phoneserver.controlplane.dto.response.ProjectResponse;
import com.phoneserver.controlplane.exception.ResourceNotFoundException;
import com.phoneserver.controlplane.model.Device;
import com.phoneserver.controlplane.model.Project;
import com.phoneserver.controlplane.model.User;
import com.phoneserver.controlplane.repository.DeviceRepository;
import com.phoneserver.controlplane.repository.ProjectRepository;
import com.phoneserver.controlplane.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            DeviceRepository deviceRepository,
            UserRepository userRepository
    ) {
        this.projectRepository = projectRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectResponse createProject(UUID userId, ProjectCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        Device device = deviceRepository.findByIdAndUser_Id(request.deviceId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found."));

        Project project = new Project();
        project.setUser(user);
        project.setDevice(device);
        project.setName(request.name().trim());
        project.setRuntime(request.runtime().trim());
        project.setFrameworkType(request.frameworkType().trim());
        project.setRepoUrl(request.repoUrl().trim());
        project.setBranch(normalizeOptional(request.branch(), "main"));
        project.setInstallCommand(normalizeOptional(request.installCommand(), null));
        project.setBuildCommand(normalizeOptional(request.buildCommand(), null));
        project.setStartCommand(request.startCommand().trim());
        project.setOutputDirectory(normalizeOptional(request.outputDirectory(), null));
        project.setLocalPort(request.localPort());
        project.setEnvConfig(copyEnvConfig(request.envConfig()));

        Project savedProject = projectRepository.save(project);
        return ProjectResponse.fromEntity(savedProject);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(UUID userId) {
        return projectRepository.findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ProjectResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID userId, UUID projectId) {
        Project project = projectRepository.findByIdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found."));

        return ProjectResponse.fromEntity(project);
    }

    private Map<String, String> copyEnvConfig(Map<String, String> envConfig) {
        return envConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(envConfig);
    }

    private String normalizeOptional(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
