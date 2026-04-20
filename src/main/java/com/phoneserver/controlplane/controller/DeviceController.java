package com.phoneserver.controlplane.controller;

import java.util.UUID;

import com.phoneserver.controlplane.dto.request.DeviceHeartbeatRequest;
import com.phoneserver.controlplane.dto.request.DeviceRegisterRequest;
import com.phoneserver.controlplane.dto.response.ApiResponse;
import com.phoneserver.controlplane.dto.response.DeviceRegistrationResponse;
import com.phoneserver.controlplane.dto.response.DeviceResponse;
import com.phoneserver.controlplane.security.AppUserPrincipal;
import com.phoneserver.controlplane.service.DeviceService;
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
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<DeviceRegistrationResponse>> registerDevice(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody DeviceRegisterRequest request
    ) {
        DeviceRegistrationResponse response = deviceService.register(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Device registered successfully.", response));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<DeviceResponse>> heartbeat(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody DeviceHeartbeatRequest request
    ) {
        DeviceResponse response = deviceService.heartbeat(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Device heartbeat recorded successfully.", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDevice(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id
    ) {
        DeviceResponse response = deviceService.getDevice(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Device retrieved successfully.", response));
    }
}

