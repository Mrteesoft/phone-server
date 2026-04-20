package com.phoneserver.controlplane.controller;

import java.util.UUID;

import com.phoneserver.controlplane.dto.request.DeploymentStartRequest;
import com.phoneserver.controlplane.dto.request.DeploymentStopRequest;
import com.phoneserver.controlplane.dto.response.ApiResponse;
import com.phoneserver.controlplane.dto.response.DeploymentResponse;
import com.phoneserver.controlplane.security.AppUserPrincipal;
import com.phoneserver.controlplane.service.DeploymentService;
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
@RequestMapping("/api/v1/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<DeploymentResponse>> startDeployment(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody DeploymentStartRequest request
    ) {
        DeploymentResponse response = deploymentService.startDeployment(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deployment started successfully.", response));
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<DeploymentResponse>> stopDeployment(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody DeploymentStopRequest request
    ) {
        DeploymentResponse response = deploymentService.stopDeployment(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Deployment stop requested successfully.", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeploymentResponse>> getDeployment(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id
    ) {
        DeploymentResponse response = deploymentService.getDeployment(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Deployment retrieved successfully.", response));
    }
}
