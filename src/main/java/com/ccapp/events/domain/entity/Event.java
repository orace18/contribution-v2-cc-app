package com.ccapp.events.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.events.domain.enums.EventLocationType;
import com.ccapp.events.domain.enums.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
@Table(name = "events", indexes = {
        @Index(name = "idx_events_status_start", columnList = "status, start_date_time"),
        @Index(name = "idx_events_organizer_created", columnList = "organizer_account_holder_id, created_at")
})
public class Event extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @Column(name = "start_date_time", nullable = false)
    private OffsetDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private OffsetDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 16)
    private EventLocationType locationType;

    @Column(name = "location_address", length = 512)
    private String locationAddress;

    @Column(name = "video_link", length = 512)
    private String videoLink;

    @Column(name = "organizer_account_holder_id", nullable = false)
    private UUID organizerAccountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EventStatus status;

    @PrePersist
    protected void initializeStatus() {
        if (status == null) {
            status = EventStatus.DRAFT;
        }
    }
}
