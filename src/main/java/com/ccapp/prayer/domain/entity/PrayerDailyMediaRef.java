package com.ccapp.prayer.domain.entity;

import com.ccapp.prayer.domain.enums.PrayerMediaUsageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@Table(name = "prayer_daily_media_refs", indexes = {
        @Index(name = "idx_prayer_daily_media_refs_daily", columnList = "daily_prayer_id")
})
public class PrayerDailyMediaRef {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "daily_prayer_id", nullable = false)
    private UUID dailyPrayerId;

    @Column(name = "media_file_id", nullable = false)
    private UUID mediaFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 16)
    private PrayerMediaUsageType usageType;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
