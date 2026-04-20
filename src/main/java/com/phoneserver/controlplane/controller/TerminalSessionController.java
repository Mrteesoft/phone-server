package com.phoneserver.controlplane.controller;

import java.util.List;
import java.util.UUID;

import com.phoneserver.controlplane.dto.request.TerminalCommandCreateRequest;
import com.phoneserver.controlplane.dto.request.TerminalSessionCreateRequest;
import com.phoneserver.controlplane.dto.response.ApiResponse;
import com.phoneserver.controlplane.dto.response.TerminalCommandResponse;
import com.phoneserver.controlplane.dto.response.TerminalSessionResponse;
import com.phoneserver.controlplane.security.AppUserPrincipal;
import com.phoneserver.controlplane.service.TerminalSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/terminal/sessions")
public class TerminalSessionController {

    private final TerminalSessionService terminalSessionService;

    public TerminalSessionController(TerminalSessionService terminalSessionService) {
        this.terminalSessionService = terminalSessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TerminalSessionResponse>> createSession(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TerminalSessionCreateRequest request
    ) {
        TerminalSessionResponse response = terminalSessionService.createSession(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Terminal session created successfully.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TerminalSessionResponse>>> listSessions(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) UUID deviceId
    ) {
        List<TerminalSessionResponse> response =
                terminalSessionService.listSessions(principal.getUserId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success("Terminal sessions retrieved successfully.", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TerminalSessionResponse>> getSession(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id
    ) {
        TerminalSessionResponse response = terminalSessionService.getSession(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Terminal session retrieved successfully.", response));
    }

    @GetMapping("/{id}/commands")
    public ResponseEntity<ApiResponse<List<TerminalCommandResponse>>> listCommands(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id
    ) {
        List<TerminalCommandResponse> response = terminalSessionService.listCommands(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Terminal commands retrieved successfully.", response));
    }

    @PostMapping("/{id}/commands")
    public ResponseEntity<ApiResponse<TerminalCommandResponse>> createCommand(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody TerminalCommandCreateRequest request
    ) {
        TerminalCommandResponse response =
                terminalSessionService.createCommand(principal.getUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Terminal command queued successfully.", response));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<TerminalSessionResponse>> closeSession(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id
    ) {
        TerminalSessionResponse response = terminalSessionService.closeSession(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Terminal session close requested successfully.", response));
    }
}
