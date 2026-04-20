package com.phoneserver.controlplane.controller;

import java.util.List;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.ProjectCreateRequest;
import com.phoneserver.controlplane.dto.response.ApiResponse;
import com.phoneserver.controlplane.dto.response.ProjectResponse;
import com.phoneserver.controlplane.security.AppUserPrincipal;
import com.phoneserver.controlplane.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        ProjectResponse response = projectService.createProject(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created successfully.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listProjects(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        List<ProjectResponse> response = projectService.listProjects(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Projects retrieved successfully.", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id
    ) {
        ProjectResponse response = projectService.getProject(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Project retrieved successfully.", response));
    }
}
