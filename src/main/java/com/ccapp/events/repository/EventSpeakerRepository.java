package com.ccapp.events.repository;

import com.ccapp.events.domain.entity.EventSpeaker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventSpeakerRepository extends JpaRepository<EventSpeaker, UUID> {

    List<EventSpeaker> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    void deleteByEventId(UUID eventId);
}
