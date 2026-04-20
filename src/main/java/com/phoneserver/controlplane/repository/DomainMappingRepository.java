package com.phoneserver.controlplane.repository;

import java.util.Optional;
import java.util.UUID;

import com.phoneserver.controlplane.model.DomainMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainMappingRepository extends JpaRepository<DomainMapping, UUID> {

    boolean existsByFullDomainIgnoreCase(String fullDomain);

    Optional<DomainMapping> findByDeployment_Id(UUID deploymentId);
}

