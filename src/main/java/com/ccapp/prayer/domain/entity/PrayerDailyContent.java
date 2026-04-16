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
@Table(name = "prayer_daily_contents", indexes = {
        @Index(name = "idx_prayer_daily_contents_date_status", columnList = "prayer_date, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_prayer_daily_contents_prayer_date", columnNames = "prayer_date")
})
public class PrayerDailyContent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "prayer_date", nullable = false)
    private LocalDate prayerDate;

    @Column(name = "code", length = 96)
    private String code;

    @Column(name = "title", length = 160)
    private String title;

    @Column(name = "scripture_reference", length = 160)
    private String scriptureReference;

    @Column(name = "scripture_text", length = 512)
    private String scriptureText;

    @Column(name = "short_content", length = 280)
    private String shortContent;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "declaration", columnDefinition = "TEXT")
    private String declaration;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "theme", length = 120)
    private String theme;

    @Column(name = "tone", length = 80)
    private String tone;

    @Column(name = "audience", length = 160)
    private String audience;

    @Column(name = "tags_csv", length = 512)
    private String tagsCsv;

    @Column(name = "language", nullable = false, length = 8)
    @Builder.Default
    private String language = "fr";

    @Column(name = "country_scope", nullable = false, length = 64)
    @Builder.Default
    private String countryScope = "AFRICA_FR";

    @Column(name = "featured", nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(name = "estimated_read_seconds", nullable = false)
    @Builder.Default
    private int estimatedReadSeconds = 45;

    @Column(name = "audio_status", nullable = false, length = 32)
    @Builder.Default
    private String audioStatus = "NONE";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private PrayerDailyPrayerStatus status = PrayerDailyPrayerStatus.DRAFT;

    @Column(name = "created_by_admin_user_id")
    private UUID createdByAdminUserId;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @PrePersist
    protected void onPersist() {
        if (status == null) {
            status = PrayerDailyPrayerStatus.DRAFT;
        }
        if (language == null || language.isBlank()) {
            language = "fr";
        }
        if (countryScope == null || countryScope.isBlank()) {
            countryScope = "AFRICA_FR";
        }
        if (estimatedReadSeconds <= 0) {
            estimatedReadSeconds = 45;
        }
        if (audioStatus == null || audioStatus.isBlank()) {
            audioStatus = "NONE";
        }
    }
}
