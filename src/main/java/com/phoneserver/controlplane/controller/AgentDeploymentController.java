package com.phoneserver.controlplane.controller;

import java.util.List;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.AgentDeploymentLogsReportRequest;
import com.phoneserver.controlplane.dto.request.AgentDeploymentStatusReportRequest;
import com.phoneserver.controlplane.dto.response.AgentDeploymentCommandResponse;
import com.phoneserver.controlplane.dto.response.ApiResponse;
import com.phoneserver.controlplane.dto.response.DeploymentResponse;
import com.phoneserver.controlplane.security.DeviceAgentPrincipal;
import com.phoneserver.controlplane.service.AgentDeploymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/deployments")
public class AgentDeploymentController {

    private final AgentDeploymentService agentDeploymentService;

    public AgentDeploymentController(AgentDeploymentService agentDeploymentService) {
        this.agentDeploymentService = agentDeploymentService;
    }

    @GetMapping("/commands/current")
    public ResponseEntity<ApiResponse<List<AgentDeploymentCommandResponse>>> getCurrentCommands(
            @AuthenticationPrincipal DeviceAgentPrincipal principal
    ) {
        List<AgentDeploymentCommandResponse> response =
                agentDeploymentService.getCurrentCommands(principal.getDeviceId());
        return ResponseEntity.ok(ApiResponse.success("Current deployment commands retrieved successfully.", response));
    }

    @GetMapping("/{id}/command")
    public ResponseEntity<ApiResponse<AgentDeploymentCommandResponse>> getDeploymentCommand(
            @AuthenticationPrincipal DeviceAgentPrincipal principal,
            @PathVariable UUID id
    ) {
        AgentDeploymentCommandResponse response =
                agentDeploymentService.getDeploymentCommand(principal.getDeviceId(), id);
        return ResponseEntity.ok(ApiResponse.success("Deployment command retrieved successfully.", response));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DeploymentResponse>> reportDeploymentStatus(
            @AuthenticationPrincipal DeviceAgentPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AgentDeploymentStatusReportRequest request
    ) {
        DeploymentResponse response =
                agentDeploymentService.reportStatus(principal.getDeviceId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Deployment status reported successfully.", response));
    }

    @PostMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<DeploymentResponse>> reportDeploymentLogs(
            @AuthenticationPrincipal DeviceAgentPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AgentDeploymentLogsReportRequest request
    ) {
        DeploymentResponse response =
                agentDeploymentService.reportLogs(principal.getDeviceId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Deployment logs reported successfully.", response));
    }
}
