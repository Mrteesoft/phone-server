package com.phoneserver.controlplane.controller;

import java.util.List;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.AgentTerminalCommandStatusReportRequest;
import com.phoneserver.controlplane.dto.request.AgentTerminalSessionStatusReportRequest;
import com.phoneserver.controlplane.dto.response.AgentTerminalCommandResponse;
import com.phoneserver.controlplane.dto.response.AgentTerminalSessionResponse;
import com.phoneserver.controlplane.dto.response.ApiResponse;
import com.phoneserver.controlplane.dto.response.TerminalCommandResponse;
import com.phoneserver.controlplane.dto.response.TerminalSessionResponse;
import com.phoneserver.controlplane.security.DeviceAgentPrincipal;
import com.phoneserver.controlplane.service.AgentTerminalService;
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
@RequestMapping("/api/v1/agent/terminal")
public class AgentTerminalController {

    private final AgentTerminalService agentTerminalService;

    public AgentTerminalController(AgentTerminalService agentTerminalService) {
        this.agentTerminalService = agentTerminalService;
    }

    @GetMapping("/sessions/current")
    public ResponseEntity<ApiResponse<List<AgentTerminalSessionResponse>>> getCurrentSessions(
            @AuthenticationPrincipal DeviceAgentPrincipal principal
    ) {
        List<AgentTerminalSessionResponse> response =
                agentTerminalService.getCurrentSessions(principal.getDeviceId());
        return ResponseEntity.ok(ApiResponse.success("Current terminal sessions retrieved successfully.", response));
    }

    @GetMapping("/sessions/{id}/commands/pending")
    public ResponseEntity<ApiResponse<List<AgentTerminalCommandResponse>>> getPendingCommands(
            @AuthenticationPrincipal DeviceAgentPrincipal principal,
            @PathVariable UUID id
    ) {
        List<AgentTerminalCommandResponse> response =
                agentTerminalService.getPendingCommands(principal.getDeviceId(), id);
        return ResponseEntity.ok(ApiResponse.success("Pending terminal commands retrieved successfully.", response));
    }

    @PostMapping("/sessions/{id}/status")
    public ResponseEntity<ApiResponse<TerminalSessionResponse>> reportSessionStatus(
            @AuthenticationPrincipal DeviceAgentPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AgentTerminalSessionStatusReportRequest request
    ) {
        TerminalSessionResponse response =
                agentTerminalService.reportSessionStatus(principal.getDeviceId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Terminal session status reported successfully.", response));
    }

    @PostMapping("/commands/{id}/status")
    public ResponseEntity<ApiResponse<TerminalCommandResponse>> reportCommandStatus(
            @AuthenticationPrincipal DeviceAgentPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AgentTerminalCommandStatusReportRequest request
    ) {
        TerminalCommandResponse response =
                agentTerminalService.reportCommandStatus(principal.getDeviceId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Terminal command status reported successfully.", response));
    }
}
