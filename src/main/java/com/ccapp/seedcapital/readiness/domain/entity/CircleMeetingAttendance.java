package com.ccapp.seedcapital.readiness.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingAttendanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circle_meeting_attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_circle_meeting_attendance_meeting_member", columnNames = {"meeting_id", "account_holder_id"})
})
public class CircleMeetingAttendance extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CircleMeetingAttendanceStatus status;

    @PrePersist
    protected void onPersist() {
        if (status == null) {
            status = CircleMeetingAttendanceStatus.PRESENT;
        }
    }
}
