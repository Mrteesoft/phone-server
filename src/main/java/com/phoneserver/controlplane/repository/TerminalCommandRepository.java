package com.phoneserver.controlplane.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.phoneserver.controlplane.model.TerminalCommand;
import com.phoneserver.controlplane.model.enums.TerminalCommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalCommandRepository extends JpaRepository<TerminalCommand, UUID> {

    List<TerminalCommand> findAllBySession_IdAndSession_User_IdOrderBySequenceNumberAsc(UUID sessionId, UUID userId);

    List<TerminalCommand> findAllBySession_IdAndStatusInOrderBySequenceNumberAsc(
            UUID sessionId,
            Collection<TerminalCommandStatus> statuses
    );

    Optional<TerminalCommand> findByIdAndSession_Device_Id(UUID id, UUID deviceId);

    Optional<TerminalCommand> findTopBySession_IdOrderBySequenceNumberDesc(UUID sessionId);
}
