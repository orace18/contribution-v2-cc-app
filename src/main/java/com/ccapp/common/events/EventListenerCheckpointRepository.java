package com.ccapp.common.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface EventListenerCheckpointRepository extends JpaRepository<EventListenerCheckpoint, UUID> {

    @Modifying
    @Query(value = """
            insert into event_listener_checkpoints (id, listener_key, event_id, processed_at)
            values (:id, :listenerKey, :eventId, :processedAt)
            on conflict (listener_key, event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("listenerKey") String listenerKey,
                       @Param("eventId") UUID eventId,
                       @Param("processedAt") OffsetDateTime processedAt);
}
