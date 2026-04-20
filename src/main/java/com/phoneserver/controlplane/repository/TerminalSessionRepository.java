package com.phoneserver.controlplane.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.phoneserver.controlplane.model.TerminalSession;
import com.phoneserver.controlplane.model.enums.TerminalSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalSessionRepository extends JpaRepository<TerminalSession, UUID> {

    Optional<TerminalSession> findByIdAndUser_Id(UUID id, UUID userId);

    Optional<TerminalSession> findByIdAndDevice_Id(UUID id, UUID deviceId);

    List<TerminalSession> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    List<TerminalSession> findAllByUser_IdAndDevice_IdOrderByCreatedAtDesc(UUID userId, UUID deviceId);

    List<TerminalSession> findAllByDevice_IdAndStatusInOrderByCreatedAtDesc(
            UUID deviceId,
            Collection<TerminalSessionStatus> statuses
    );
}
