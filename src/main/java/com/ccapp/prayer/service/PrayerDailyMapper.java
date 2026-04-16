package com.ccapp.prayer.service;

import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerDailyMediaRef;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.dto.AdminDailyPrayerResponse;
import com.ccapp.prayer.dto.DailyPrayerResponse;
import com.ccapp.prayer.dto.PrayerMediaRefResponse;
import com.ccapp.prayer.repository.PrayerDailyMediaRefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PrayerDailyMapper {

    private final PrayerDailyMediaRefRepository prayerDailyMediaRefRepository;
    private final MediaFileRepository mediaFileRepository;

    DailyPrayerResponse toMobileResponse(PrayerDailyContent content,
                                         boolean completedToday,
                                         PrayerDailyPrayerResponseType todayResponseType) {
        return new DailyPrayerResponse(
                content.getId(),
                content.getPrayerDate(),
                content.getTitle(),
                content.getScriptureReference(),
                content.getScriptureText(),
                content.getShortContent(),
                content.getContent(),
                content.getDeclaration(),
                loadMediaRefs(content.getId()),
                completedToday,
                todayResponseType,
                content.getPublishedAt()
        );
    }

    AdminDailyPrayerResponse toAdminResponse(PrayerDailyContent content) {
        return new AdminDailyPrayerResponse(
                content.getId(),
                content.getPrayerDate(),
                content.getStatus(),
                content.getCode(),
                content.getTitle(),
                content.getScriptureReference(),
                content.getScriptureText(),
                content.getShortContent(),
                content.getContent(),
                content.getDeclaration(),
                content.getCategory(),
                content.getTheme(),
                content.getTone(),
                content.getAudience(),
                content.getTagsCsv(),
                content.getLanguage(),
                content.getCountryScope(),
                content.isFeatured(),
                content.getPriority(),
                content.getEstimatedReadSeconds(),
                content.getAudioStatus(),
                loadMediaRefs(content.getId()),
                content.getCreatedByAdminUserId(),
                content.getCreatedAt(),
                content.getUpdatedAt(),
                content.getPublishedAt()
        );
    }

    private List<PrayerMediaRefResponse> loadMediaRefs(UUID dailyPrayerId) {
        return prayerDailyMediaRefRepository.findByDailyPrayerIdOrderBySortOrderAsc(dailyPrayerId).stream()
                .map(this::toMediaRefResponse)
                .toList();
    }

    private PrayerMediaRefResponse toMediaRefResponse(PrayerDailyMediaRef ref) {
        MediaFile mediaFile = mediaFileRepository.findById(ref.getMediaFileId()).orElse(null);
        return new PrayerMediaRefResponse(
                ref.getMediaFileId(),
                ref.getUsageType(),
                ref.getSortOrder(),
                mediaFile == null ? null : mediaFile.getContentType(),
                null
        );
    }
}
