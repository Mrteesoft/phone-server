package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, String> errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Map.of(), Instant.now());
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, Map.of(), Instant.now());
    }

    public static ApiResponse<Void> error(String message, Map<String, String> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now());
    }
}

