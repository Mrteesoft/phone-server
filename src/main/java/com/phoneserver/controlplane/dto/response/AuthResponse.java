package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.User;

public record AuthResponse(
        UUID userId,
        String email,
        String token,
        String tokenType,
        Instant issuedAt
) {

    public static AuthResponse fromEntity(User user, String token, Instant issuedAt) {
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                token,
                "Bearer",
                issuedAt
        );
    }
}

