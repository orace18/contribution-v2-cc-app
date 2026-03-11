package com.ccapp.common.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_listener_checkpoints",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_listener_checkpoints_listener_event",
                columnNames = {"listener_key", "event_id"}))
public class EventListenerCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listener_key", nullable = false, length = 128)
    private String listenerKey;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;
}
