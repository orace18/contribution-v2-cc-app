package com.ccapp.prayer.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.prayer.domain.enums.PrayerSuggestionAudioStatus;
import com.ccapp.prayer.domain.enums.PrayerSuggestionCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "prayer_suggestion_templates", indexes = {
        @Index(name = "idx_prayer_suggestion_templates_active_sort", columnList = "active, sort_order"),
        @Index(name = "idx_prayer_suggestion_templates_category", columnList = "category")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_prayer_suggestion_templates_key", columnNames = "template_key")
})
public class PrayerSuggestionTemplate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_key", nullable = false, length = 64)
    private String templateKey;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 64)
    private PrayerSuggestionCategory category;

    @Column(name = "tags_csv", length = 255)
    private String tagsCsv;

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_status", nullable = false, length = 16)
    @Builder.Default
    private PrayerSuggestionAudioStatus audioStatus = PrayerSuggestionAudioStatus.NONE;

    @Column(name = "audio_url", length = 512)
    private String audioUrl;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
