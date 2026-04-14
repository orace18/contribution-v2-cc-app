package com.ccapp.prayer.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.prayer.domain.entity.PrayerDailyCompletion;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerDailyMediaRef;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.domain.enums.PrayerMediaUsageType;
import com.ccapp.prayer.dto.PrayerMediaRefResponse;
import com.ccapp.prayer.dto.PrayerStreakResponse;
import com.ccapp.prayer.dto.PrayerTodayRecommendationResponse;
import com.ccapp.prayer.repository.PrayerDailyCompletionRepository;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import com.ccapp.prayer.repository.PrayerDailyMediaRefRepository;
import com.ccapp.prayer.repository.PrayerSupportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerRecommendationService {

    private final PrayerDailyContentRepository    prayerDailyContentRepository;
    private final PrayerDailyCompletionRepository  prayerDailyCompletionRepository;
    private final PrayerDailyMediaRefRepository    prayerDailyMediaRefRepository;
    private final PrayerSupportRepository          prayerSupportRepository;
    private final PrayerEngagementService          prayerEngagementService;
    private final PrayerTimezoneResolver           prayerTimezoneResolver;
    private final MediaFileRepository             mediaFileRepository;
    private final ObjectStorageService            objectStorageService;

    @Value("${media.download-url-ttl-minutes:15}")
    private long downloadUrlTtlMinutes;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PrayerTodayRecommendationResponse getTodayRecommendation(
            AccountHolder accountHolder, String requestedTimezone) {

        PrayerTimezoneResolver.PrayerLocalContext ctx =
                prayerTimezoneResolver.resolve(accountHolder, requestedTimezone);
        UUID accountHolderId = accountHolder.getId();

        // 1. All PUBLISHED prayers
        List<PrayerDailyContent> allPublished = prayerDailyContentRepository
                .findByStatusOrderByPrayerDateDesc(PrayerDailyPrayerStatus.PUBLISHED);

        // 2. Prayer IDs already completed by this user
        Set<UUID> completedIds = prayerDailyCompletionRepository
                .findByAccountHolderIdOrderByPrayerDateDesc(accountHolderId).stream()
                .map(PrayerDailyCompletion::getDailyPrayerId)
                .collect(Collectors.toSet());

        // 3. Prefer uncompleted; fall back to all if everything is done
        List<PrayerDailyContent> pool = allPublished.stream()
                .filter(p -> !completedIds.contains(p.getId()))
                .collect(Collectors.toList());
        if (pool.isEmpty()) {
            pool = allPublished;
            log.info("[recommendation] all prayers completed — falling back to full pool accountHolderId={}", accountHolderId);
        }

        // 4. Score and select best candidate
        PrayerDailyContent selected = selectBest(pool, accountHolderId, ctx);

        // 5. Completion status for today's date
        PrayerDailyCompletion todayCompletion = prayerDailyCompletionRepository
                .findByAccountHolderIdAndPrayerDate(accountHolderId, ctx.localDate())
                .orElse(null);

        // 6. Streak — reuses PrayerEngagementService calculation
        PrayerStreakResponse streak = prayerEngagementService
                .getStreakResponse(accountHolderId, ctx.localDate());

        // 7. Community support received count
        int supportReceived = Math.toIntExact(
                prayerSupportRepository.countReceivedByAccountHolderId(accountHolderId));

        List<PrayerMediaRefResponse> mediaRefs = loadMediaRefs(selected.getId());

        log.info("[recommendation] selected prayerId={} prayerDate={} accountHolderId={} uncompletedPool={}",
                selected.getId(), selected.getPrayerDate(), accountHolderId, pool.size());

        return new PrayerTodayRecommendationResponse(
                selected.getId(),
                selected.getPrayerDate(),
                selected.getTitle(),
                selected.getScriptureReference(),
                selected.getContent(),
                mediaRefs,
                todayCompletion != null,
                todayCompletion == null ? null : todayCompletion.getResponseType(),
                selected.getPublishedAt(),
                streak.currentStreak(),
                streak.longestStreak(),
                supportReceived
        );
    }

    // ── Selection algorithm ───────────────────────────────────────────────────

    private PrayerDailyContent selectBest(List<PrayerDailyContent> pool,
                                          UUID accountHolderId,
                                          PrayerTimezoneResolver.PrayerLocalContext ctx) {
        if (pool.size() == 1) return pool.get(0);

        int hour      = ctx.now().toLocalTime().getHour();
        int dayOfWeek = ctx.localDate().getDayOfWeek().getValue(); // 1=Mon … 7=Sun
        long totalCompletions = prayerDailyCompletionRepository.countByAccountHolderId(accountHolderId);

        // Deterministic daily seed per user — stable within the same day
        int daySeed = Math.abs((ctx.localDate().toString() + accountHolderId).hashCode());

        // Pre-compute which prayers have an AUDIO media ref (single pass)
        Set<UUID> prayersWithAudio = pool.stream()
                .filter(p -> prayerDailyMediaRefRepository
                        .findByDailyPrayerIdOrderBySortOrderAsc(p.getId()).stream()
                        .anyMatch(ref -> ref.getUsageType() == PrayerMediaUsageType.AUDIO))
                .map(PrayerDailyContent::getId)
                .collect(Collectors.toSet());

        return pool.stream()
                .max(Comparator.comparingDouble(prayer ->
                        score(prayer,
                              prayersWithAudio.contains(prayer.getId()),
                              ctx.localDate(),
                              hour,
                              dayOfWeek,
                              totalCompletions,
                              daySeed)))
                .orElse(pool.get(0));
    }

    /**
     * Scoring function — higher is better.
     *
     * <ul>
     *   <li>+20 if prayer has an audio attachment (user actively uses audio)</li>
     *   <li>+15/10/5 by recency (7d / 30d / 90d since prayer date)</li>
     *   <li>+8/12/5 by time of day (morning / evening / night)</li>
     *   <li>+10/5 by day of week (Sunday / Saturday)</li>
     *   <li>+8 for shorter content when user is new (< 3 completions)</li>
     *   <li>0–9 deterministic daily variance for tie-breaking</li>
     * </ul>
     */
    private double score(PrayerDailyContent prayer,
                         boolean hasAudio,
                         LocalDate today,
                         int hour,
                         int dayOfWeek,
                         long totalCompletions,
                         int daySeed) {
        double s = 0;

        // Audio preference
        if (hasAudio) s += 20;

        // Recency (days since prayer_date)
        long daysDelta = ChronoUnit.DAYS.between(prayer.getPrayerDate(), today);
        if (daysDelta <= 7)        s += 15;
        else if (daysDelta <= 30)  s += 10;
        else if (daysDelta <= 90)  s +=  5;

        // Time of day
        if      (hour >= 5  && hour < 12) s +=  8; // morning — fresh start
        else if (hour >= 17 && hour < 22) s += 12; // evening — reflection
        else if (hour >= 22 || hour < 5)  s +=  5; // night — quiet moment

        // Day of week
        if      (dayOfWeek == 7) s += 10; // Sunday — peak engagement
        else if (dayOfWeek == 6) s +=  5; // Saturday

        // New user comfort — prefer shorter prayers
        if (totalCompletions < 3 && prayer.getContent().length() < 300) s += 8;

        // Deterministic daily variance (0–9) for stable tie-breaking within a day
        s += Math.abs(daySeed + prayer.getId().hashCode()) % 10;

        return s;
    }

    // ── Media loading ─────────────────────────────────────────────────────────

    private List<PrayerMediaRefResponse> loadMediaRefs(UUID dailyPrayerId) {
        return prayerDailyMediaRefRepository
                .findByDailyPrayerIdOrderBySortOrderAsc(dailyPrayerId).stream()
                .map(this::toMediaRefResponse)
                .toList();
    }

    private PrayerMediaRefResponse toMediaRefResponse(PrayerDailyMediaRef ref) {
        MediaFile mediaFile = mediaFileRepository.findById(ref.getMediaFileId()).orElse(null);
        String downloadUrl = null;
        if (mediaFile != null && mediaFile.getStatus() == MediaStatus.READY) {
            try {
                downloadUrl = objectStorageService.createDownloadUrl(
                        mediaFile.getBucket(),
                        mediaFile.getObjectKey(),
                        Duration.ofMinutes(downloadUrlTtlMinutes)
                ).url();
            } catch (Exception ex) {
                log.warn("[recommendation] unable to build download URL dailyPrayerId={} mediaFileId={}",
                        ref.getDailyPrayerId(), ref.getMediaFileId(), ex);
            }
        }
        return new PrayerMediaRefResponse(
                ref.getMediaFileId(),
                ref.getUsageType(),
                ref.getSortOrder(),
                mediaFile == null ? null : mediaFile.getContentType(),
                downloadUrl
        );
    }
}
