package com.phoneserver.controlplane.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.phoneserver.controlplane.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<Project> findByIdAndUser_Id(UUID id, UUID userId);
}

