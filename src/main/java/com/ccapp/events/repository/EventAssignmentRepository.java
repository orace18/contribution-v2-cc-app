package com.ccapp.events.repository;

import com.ccapp.events.domain.entity.EventAssignment;
import com.ccapp.events.domain.enums.EventAssignmentRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventAssignmentRepository extends JpaRepository<EventAssignment, UUID> {

    boolean existsByEventIdAndAccountHolderIdAndRoleIn(UUID eventId,
                                                       UUID accountHolderId,
                                                       Collection<EventAssignmentRole> roles);

    List<EventAssignment> findByAccountHolderId(UUID accountHolderId);

    List<EventAssignment> findByEventId(UUID eventId);

    void deleteByEventIdAndRole(UUID eventId, EventAssignmentRole role);
}
