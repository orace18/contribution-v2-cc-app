package com.ccapp.events.repository;

import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByOrganizerAccountHolderIdOrderByCreatedAtDesc(UUID organizerAccountHolderId);

    List<Event> findByStatusOrderByStartDateTimeAsc(EventStatus status);

    List<Event> findByStatusInOrderByStartDateTimeAsc(Collection<EventStatus> statuses);

    List<Event> findByStatusAndEndDateTimeLessThanEqual(EventStatus status, OffsetDateTime endDateTime);
}
