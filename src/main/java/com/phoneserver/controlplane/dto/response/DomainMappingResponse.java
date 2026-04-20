package com.phoneserver.controlplane.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.phoneserver.controlplane.model.DomainMapping;
import com.phoneserver.controlplane.model.enums.DomainMappingStatus;
import com.phoneserver.controlplane.model.enums.ProxyType;

public record DomainMappingResponse(
        UUID id,
        String fullDomain,
        String baseDomain,
        String subdomain,
        String pathPrefix,
        ProxyType proxyType,
        Integer targetPort,
        DomainMappingStatus status,
        Instant assignedAt
) {

    public static DomainMappingResponse fromEntity(DomainMapping domainMapping) {
        return new DomainMappingResponse(
                domainMapping.getId(),
                domainMapping.getFullDomain(),
                domainMapping.getBaseDomain(),
                domainMapping.getSubdomain(),
                domainMapping.getPathPrefix(),
                domainMapping.getProxyType(),
                domainMapping.getTargetPort(),
                domainMapping.getStatus(),
                domainMapping.getAssignedAt()
        );
    }
}

