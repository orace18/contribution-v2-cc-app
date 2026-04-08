package com.ccapp.prayer.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prayer_daily_mission_progress", indexes = {
        @Index(name = "idx_prayer_daily_mission_progress_account_date", columnList = "account_holder_id, mission_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_prayer_daily_mission_progress_account_date_code", columnNames = {
                "account_holder_id", "mission_date", "mission_code"
        })
})
public class PrayerUserDailyMission extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "mission_date", nullable = false)
    private LocalDate missionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_code", nullable = false, length = 64)
    private PrayerDailyMissionCode missionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private PrayerDailyMissionStatus status = PrayerDailyMissionStatus.PENDING;

    @Column(name = "progress_value", nullable = false)
    @Builder.Default
    private int progressValue = 0;

    @Column(name = "target_value", nullable = false)
    private int targetValue;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onPersist() {
        if (status == null) {
            status = PrayerDailyMissionStatus.PENDING;
        }
    }
}
