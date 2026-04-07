package com.ccapp.prayer.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prayer_daily_contents", indexes = {
        @Index(name = "idx_prayer_daily_contents_date_status", columnList = "prayer_date, status")
})
public class PrayerDailyContent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "prayer_date", nullable = false)
    private LocalDate prayerDate;

    @Column(name = "title", length = 160)
    private String title;

    @Column(name = "scripture_reference", length = 160)
    private String scriptureReference;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private PrayerDailyPrayerStatus status = PrayerDailyPrayerStatus.DRAFT;

    @Column(name = "created_by_admin_user_id", nullable = false)
    private UUID createdByAdminUserId;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @PrePersist
    protected void onPersist() {
        if (status == null) {
            status = PrayerDailyPrayerStatus.DRAFT;
        }
    }
}
