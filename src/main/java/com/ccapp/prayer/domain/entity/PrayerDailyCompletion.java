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
@Table(name = "prayer_daily_completions", indexes = {
        @Index(name = "idx_prayer_daily_completions_account_date", columnList = "account_holder_id, prayer_date"),
        @Index(name = "idx_prayer_daily_completions_daily", columnList = "daily_prayer_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_prayer_daily_completions_account_date", columnNames = {"account_holder_id", "prayer_date"})
})
public class PrayerDailyCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "daily_prayer_id", nullable = false)
    private UUID dailyPrayerId;

    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "prayer_date", nullable = false)
    private LocalDate prayerDate;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, length = 32)
    @Builder.Default
    private PrayerDailyPrayerResponseType responseType = PrayerDailyPrayerResponseType.PRAYED;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (completedAt == null) {
            completedAt = OffsetDateTime.now();
        }
        if (responseType == null) {
            responseType = PrayerDailyPrayerResponseType.PRAYED;
        }
    }
}
