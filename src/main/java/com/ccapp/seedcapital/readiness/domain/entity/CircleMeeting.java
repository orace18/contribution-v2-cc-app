package com.ccapp.seedcapital.readiness.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "circle_meetings")
public class CircleMeeting extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "created_by_account_holder_id", nullable = false)
    private UUID createdByAccountHolderId;

    @Column(name = "meeting_at", nullable = false)
    private OffsetDateTime meetingAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleMeetingStatus status;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "attendance_count", nullable = false)
    private Integer attendanceCount;

    @Column(name = "quorum_reached", nullable = false)
    private boolean quorumReached;

    @Column(name = "notes", length = 1000)
    private String notes;

    @PrePersist
    protected void onPersist() {
        if (status == null) {
            status = CircleMeetingStatus.COMPLETED;
        }
        if (participantCount == null) {
            participantCount = 0;
        }
        if (attendanceCount == null) {
            attendanceCount = 0;
        }
    }
}
