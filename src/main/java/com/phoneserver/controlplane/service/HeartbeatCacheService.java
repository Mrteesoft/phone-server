package com.phoneserver.controlplane.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class HeartbeatCacheService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatCacheService.class);
    private static final Duration HEARTBEAT_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public HeartbeatCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordHeartbeat(UUID deviceId, Instant timestamp) {
        try {
            redisTemplate.opsForValue().set(buildKey(deviceId), timestamp.toString(), HEARTBEAT_TTL);
        } catch (RuntimeException exception) {
            log.warn("Failed to cache heartbeat for device {}", deviceId, exception);
        }
    }

    public boolean hasRecentHeartbeat(UUID deviceId) {
        try {
            Boolean hasKey = redisTemplate.hasKey(buildKey(deviceId));
            return Boolean.TRUE.equals(hasKey);
        } catch (RuntimeException exception) {
            log.warn("Failed to query heartbeat cache for device {}", deviceId, exception);
            return false;
        }
    }

    private String buildKey(UUID deviceId) {
        return "device:heartbeat:" + deviceId;
    }
}

