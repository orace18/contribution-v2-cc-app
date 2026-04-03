package com.ccapp.events.repository;

import com.ccapp.events.domain.entity.EventStoreSyncJob;
import com.ccapp.events.domain.enums.EventStoreSyncJobStatus;
import com.ccapp.events.domain.enums.EventStoreSyncJobType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventStoreSyncJobRepository extends JpaRepository<EventStoreSyncJob, UUID> {

    List<EventStoreSyncJob> findTop25ByStatusOrderByCreatedAtAsc(EventStoreSyncJobStatus status);

    boolean existsByTicketTypeIdAndTypeAndStatusIn(UUID ticketTypeId,
                                                   EventStoreSyncJobType type,
                                                   Collection<EventStoreSyncJobStatus> statuses);
}
