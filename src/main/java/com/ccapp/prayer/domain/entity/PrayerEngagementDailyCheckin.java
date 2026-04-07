package com.ccapp.prayer.domain.entity;

import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
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
@Table(name = "prayer_engagement_daily_checkins", indexes = {
        @Index(name = "idx_prayer_checkins_account_date", columnList = "account_holder_id, local_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_prayer_checkins_account_date", columnNames = {"account_holder_id", "local_date"})
})
public class PrayerEngagementDailyCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, length = 32)
    @Builder.Default
    private PrayerDailyPrayerResponseType responseType = PrayerDailyPrayerResponseType.PRAYED;

    @Column(name = "source_reference_id")
    private UUID sourceReferenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (responseType == null) {
            responseType = PrayerDailyPrayerResponseType.PRAYED;
        }
    }
}
