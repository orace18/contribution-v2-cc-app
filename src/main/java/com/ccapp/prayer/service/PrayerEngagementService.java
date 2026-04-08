package com.ccapp.prayer.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.prayer.domain.entity.PrayerDailyCompletion;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerEngagementDailyCheckin;
import com.ccapp.prayer.domain.entity.PrayerUserDailyMission;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionStatus;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.dto.DailyPrayerCompletionResponse;
import com.ccapp.prayer.dto.DailyPrayerResponse;
import com.ccapp.prayer.dto.PrayerDashboardResponse;
import com.ccapp.prayer.dto.PrayerEngagementStatsResponse;
import com.ccapp.prayer.dto.PrayerMissionProgressResponse;
import com.ccapp.prayer.dto.PrayerRecentDayResponse;
import com.ccapp.prayer.dto.PrayerStreakResponse;
import com.ccapp.prayer.repository.PrayerAmenRepository;
import com.ccapp.prayer.repository.PrayerCommentRepository;
import com.ccapp.prayer.repository.PrayerDailyCompletionRepository;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import com.ccapp.prayer.repository.PrayerDailyMissionDefinitionRepository;
import com.ccapp.prayer.repository.PrayerEngagementDailyCheckinRepository;
import com.ccapp.prayer.repository.PrayerPostRepository;
import com.ccapp.prayer.repository.PrayerSupportRepository;
import com.ccapp.prayer.repository.PrayerUserDailyMissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerEngagementService {

    private static final int RECENT_DAY_WINDOW = 7;

    private final PrayerDailyContentRepository prayerDailyContentRepository;
    private final PrayerDailyCompletionRepository prayerDailyCompletionRepository;
    private final PrayerEngagementDailyCheckinRepository prayerEngagementDailyCheckinRepository;
    private final PrayerDailyMissionDefinitionRepository prayerDailyMissionDefinitionRepository;
    private final PrayerUserDailyMissionRepository prayerUserDailyMissionRepository;
    private final PrayerPostRepository prayerPostRepository;
    private final PrayerAmenRepository prayerAmenRepository;
    private final PrayerSupportRepository prayerSupportRepository;
    private final PrayerCommentRepository prayerCommentRepository;
    private final PrayerDailyMapper prayerDailyMapper;
    private final PrayerTimezoneResolver prayerTimezoneResolver;

    @Transactional(readOnly = true)
    public DailyPrayerResponse getCurrentDailyPrayer(AccountHolder accountHolder, String requestedTimezone) {
        PrayerTimezoneResolver.PrayerLocalContext localContext = prayerTimezoneResolver.resolve(accountHolder, requestedTimezone);
        PrayerDailyContent dailyContent = findPublishedDailyPrayer(localContext.localDate());
        PrayerDailyCompletion completion = prayerDailyCompletionRepository
                .findByAccountHolderIdAndPrayerDate(accountHolder.getId(), localContext.localDate())
                .orElse(null);
        return prayerDailyMapper.toMobileResponse(
                dailyContent,
                completion != null,
                completion == null ? null : completion.getResponseType()
        );
    }

    @Transactional
    public DailyPrayerCompletionResponse completeCurrentDailyPrayer(UUID actorUserId,
                                                                    AccountHolder accountHolder,
                                                                    String requestedTimezone,
                                                                    PrayerDailyPrayerResponseType responseType) {
        PrayerTimezoneResolver.PrayerLocalContext localContext = prayerTimezoneResolver.resolve(accountHolder, requestedTimezone);
        PrayerDailyContent dailyContent = findPublishedDailyPrayer(localContext.localDate());

        expireHistoricalMissions(accountHolder.getId(), localContext.localDate());
        ensureCurrentDayMissions(accountHolder.getId(), localContext.localDate());

        PrayerDailyCompletion completion = prayerDailyCompletionRepository
                .findByAccountHolderIdAndPrayerDate(accountHolder.getId(), localContext.localDate())
                .orElse(null);
        if (completion == null) {
            completion = prayerDailyCompletionRepository.save(PrayerDailyCompletion.builder()
                    .dailyPrayerId(dailyContent.getId())
                    .accountHolderId(accountHolder.getId())
                    .actorUserId(actorUserId)
                    .prayerDate(localContext.localDate())
                    .timezone(localContext.timezone())
                    .responseType(responseType)
                    .completedAt(localContext.now())
                    .build());
            log.info("Prayer daily completion created accountHolderId={} prayerDate={} responseType={}",
                    accountHolder.getId(), localContext.localDate(), responseType);
        } else if (completion.getResponseType() != responseType) {
            completion.setResponseType(responseType);
            completion = prayerDailyCompletionRepository.save(completion);
            log.info("Prayer daily completion response updated accountHolderId={} prayerDate={} responseType={}",
                    accountHolder.getId(), localContext.localDate(), responseType);
        }

        upsertDailyCheckin(actorUserId, accountHolder.getId(), localContext, dailyContent.getId(), completion.getResponseType());
        markMissionProgress(accountHolder.getId(), localContext.localDate(), PrayerDailyMissionCode.READ_DAILY_PRAYER);

        StreakSnapshot streakSnapshot = calculateStreak(accountHolder.getId(), localContext.localDate());
        List<PrayerDailyMissionCode> completedMissionCodes = loadCurrentDayMissions(accountHolder.getId(), localContext.localDate()).stream()
                .filter(mission -> mission.getStatus() == PrayerDailyMissionStatus.COMPLETED)
                .map(PrayerUserDailyMission::getMissionCode)
                .toList();

        return new DailyPrayerCompletionResponse(
                dailyContent.getId(),
                localContext.localDate(),
                completion.getCompletedAt(),
                completion.getResponseType(),
                streakSnapshot.currentStreak(),
                streakSnapshot.longestStreak(),
                completedMissionCodes
        );
    }

    @Transactional
    public PrayerDashboardResponse getPrayerDashboard(AccountHolder accountHolder, String requestedTimezone) {
        PrayerTimezoneResolver.PrayerLocalContext localContext = prayerTimezoneResolver.resolve(accountHolder, requestedTimezone);
        expireHistoricalMissions(accountHolder.getId(), localContext.localDate());
        List<PrayerUserDailyMission> todayMissions = ensureCurrentDayMissions(accountHolder.getId(), localContext.localDate());

        boolean completedToday = prayerDailyCompletionRepository
                .findByAccountHolderIdAndPrayerDate(accountHolder.getId(), localContext.localDate())
                .isPresent();
        StreakSnapshot streakSnapshot = calculateStreak(accountHolder.getId(), localContext.localDate());

        return new PrayerDashboardResponse(
                localContext.timezone(),
                localContext.localDate(),
                completedToday,
                new PrayerStreakResponse(
                        streakSnapshot.currentStreak(),
                        streakSnapshot.longestStreak(),
                        streakSnapshot.lastCompletedDate(),
                        streakSnapshot.recentDays()
                ),
                buildEngagementStats(accountHolder.getId()),
                toMissionResponses(todayMissions)
        );
    }

    @Transactional
    public void recordTrackedMissionAction(AccountHolder accountHolder, PrayerDailyMissionCode missionCode) {
        PrayerTimezoneResolver.PrayerLocalContext localContext = prayerTimezoneResolver.resolve(accountHolder, null);
        expireHistoricalMissions(accountHolder.getId(), localContext.localDate());
        ensureCurrentDayMissions(accountHolder.getId(), localContext.localDate());
        markMissionProgress(accountHolder.getId(), localContext.localDate(), missionCode);
    }

    private PrayerDailyContent findPublishedDailyPrayer(LocalDate localDate) {
        return prayerDailyContentRepository.findByPrayerDateAndStatus(localDate, PrayerDailyPrayerStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("PRAYER_DAILY_NOT_FOUND"));
    }

    private void upsertDailyCheckin(UUID actorUserId,
                                    UUID accountHolderId,
                                    PrayerTimezoneResolver.PrayerLocalContext localContext,
                                    UUID sourceReferenceId,
                                    PrayerDailyPrayerResponseType responseType) {
        PrayerEngagementDailyCheckin existing = prayerEngagementDailyCheckinRepository
                .findByAccountHolderIdAndLocalDate(accountHolderId, localContext.localDate())
                .orElse(null);
        if (existing == null) {
            prayerEngagementDailyCheckinRepository.save(PrayerEngagementDailyCheckin.builder()
                    .accountHolderId(accountHolderId)
                    .actorUserId(actorUserId)
                    .localDate(localContext.localDate())
                    .timezone(localContext.timezone())
                    .responseType(responseType)
                    .sourceReferenceId(sourceReferenceId)
                    .createdAt(localContext.now())
                    .build());
            return;
        }

        existing.setActorUserId(actorUserId);
        existing.setTimezone(localContext.timezone());
        existing.setResponseType(responseType);
        existing.setSourceReferenceId(sourceReferenceId);
        prayerEngagementDailyCheckinRepository.save(existing);
    }

    private void expireHistoricalMissions(UUID accountHolderId, LocalDate localDate) {
        List<PrayerUserDailyMission> staleMissions = prayerUserDailyMissionRepository
                .findByAccountHolderIdAndMissionDateBeforeAndStatusOrderByMissionDateAscMissionCodeAsc(
                        accountHolderId,
                        localDate,
                        PrayerDailyMissionStatus.PENDING
                );
        if (staleMissions.isEmpty()) {
            return;
        }
        staleMissions.forEach(mission -> mission.setStatus(PrayerDailyMissionStatus.EXPIRED));
        prayerUserDailyMissionRepository.saveAll(staleMissions);
    }

    private List<PrayerUserDailyMission> ensureCurrentDayMissions(UUID accountHolderId, LocalDate localDate) {
        List<PrayerUserDailyMission> currentMissions = loadCurrentDayMissions(accountHolderId, localDate);
        Map<PrayerDailyMissionCode, PrayerUserDailyMission> byCode = new EnumMap<>(PrayerDailyMissionCode.class);
        currentMissions.forEach(mission -> byCode.put(mission.getMissionCode(), mission));

        List<PrayerUserDailyMission> created = new ArrayList<>();
        prayerDailyMissionDefinitionRepository.findByActiveTrueOrderBySortOrderAsc().forEach(definition -> {
            if (!byCode.containsKey(definition.getMissionCode())) {
                created.add(prayerUserDailyMissionRepository.save(PrayerUserDailyMission.builder()
                        .accountHolderId(accountHolderId)
                        .missionDate(localDate)
                        .missionCode(definition.getMissionCode())
                        .status(PrayerDailyMissionStatus.PENDING)
                        .progressValue(0)
                        .targetValue(definition.getTargetValue())
                        .build()));
            }
        });

        if (!created.isEmpty()) {
            currentMissions = new ArrayList<>(currentMissions);
            currentMissions.addAll(created);
        }
        return sortMissionRows(currentMissions);
    }

    private List<PrayerUserDailyMission> loadCurrentDayMissions(UUID accountHolderId, LocalDate localDate) {
        return prayerUserDailyMissionRepository.findByAccountHolderIdAndMissionDateOrderByMissionCodeAsc(accountHolderId, localDate);
    }

    private List<PrayerUserDailyMission> sortMissionRows(List<PrayerUserDailyMission> missions) {
        Map<PrayerDailyMissionCode, Integer> sortOrderByCode = new EnumMap<>(PrayerDailyMissionCode.class);
        prayerDailyMissionDefinitionRepository.findByActiveTrueOrderBySortOrderAsc().forEach(definition ->
                sortOrderByCode.put(definition.getMissionCode(), definition.getSortOrder()));

        return missions.stream()
                .sorted(Comparator
                        .comparingInt((PrayerUserDailyMission mission) -> sortOrderByCode.getOrDefault(mission.getMissionCode(), Integer.MAX_VALUE))
                        .thenComparing(PrayerUserDailyMission::getMissionCode))
                .toList();
    }

    private void markMissionProgress(UUID accountHolderId, LocalDate localDate, PrayerDailyMissionCode missionCode) {
        PrayerUserDailyMission mission = prayerUserDailyMissionRepository
                .findByAccountHolderIdAndMissionDateAndMissionCode(accountHolderId, localDate, missionCode)
                .orElse(null);
        if (mission == null || mission.getStatus() == PrayerDailyMissionStatus.EXPIRED) {
            return;
        }
        if (mission.getStatus() == PrayerDailyMissionStatus.COMPLETED) {
            return;
        }

        int nextProgress = Math.min(mission.getTargetValue(), mission.getProgressValue() + 1);
        mission.setProgressValue(nextProgress);
        if (nextProgress >= mission.getTargetValue()) {
            mission.setStatus(PrayerDailyMissionStatus.COMPLETED);
            mission.setCompletedAt(OffsetDateTime.now());
        }
        prayerUserDailyMissionRepository.save(mission);
    }

    private PrayerEngagementStatsResponse buildEngagementStats(UUID accountHolderId) {
        return new PrayerEngagementStatsResponse(
                Math.toIntExact(prayerPostRepository.countByAuthorAccountHolderIdAndPostType(accountHolderId, PrayerPostType.REQUEST)),
                Math.toIntExact(prayerAmenRepository.countByAccountHolderId(accountHolderId)),
                Math.toIntExact(prayerSupportRepository.countByAccountHolderId(accountHolderId)),
                Math.toIntExact(prayerSupportRepository.countReceivedByAccountHolderId(accountHolderId)),
                Math.toIntExact(prayerCommentRepository.countByAccountHolderId(accountHolderId)),
                Math.toIntExact(prayerCommentRepository.countReceivedByAccountHolderId(accountHolderId)),
                Math.toIntExact(prayerDailyCompletionRepository.countByAccountHolderId(accountHolderId))
        );
    }

    private List<PrayerMissionProgressResponse> toMissionResponses(List<PrayerUserDailyMission> missions) {
        Map<PrayerDailyMissionCode, com.ccapp.prayer.domain.entity.PrayerDailyMissionDefinition> definitions = new HashMap<>();
        prayerDailyMissionDefinitionRepository.findByActiveTrueOrderBySortOrderAsc().forEach(definition ->
                definitions.put(definition.getMissionCode(), definition));

        return sortMissionRows(missions).stream()
                .map(mission -> {
                    com.ccapp.prayer.domain.entity.PrayerDailyMissionDefinition definition = definitions.get(mission.getMissionCode());
                    return new PrayerMissionProgressResponse(
                            mission.getMissionCode(),
                            definition == null ? mission.getMissionCode().name() : definition.getTitle(),
                            definition == null ? mission.getMissionCode().name() : definition.getDescription(),
                            mission.getStatus(),
                            mission.getProgressValue(),
                            mission.getTargetValue(),
                            mission.getCompletedAt()
                    );
                })
                .toList();
    }

    private StreakSnapshot calculateStreak(UUID accountHolderId, LocalDate referenceDate) {
        List<LocalDate> completedDates = prayerDailyCompletionRepository.findByAccountHolderIdOrderByPrayerDateAsc(accountHolderId).stream()
                .map(PrayerDailyCompletion::getPrayerDate)
                .distinct()
                .toList();
        if (completedDates.isEmpty()) {
            return new StreakSnapshot(0, 0, null, buildRecentDays(referenceDate, Set.of()));
        }

        int longest = 0;
        int rolling = 0;
        LocalDate previous = null;
        for (LocalDate date : completedDates) {
            if (previous == null || date.equals(previous.plusDays(1))) {
                rolling++;
            } else {
                rolling = 1;
            }
            longest = Math.max(longest, rolling);
            previous = date;
        }

        LocalDate lastCompletedDate = completedDates.getLast();
        int current = 0;
        if (!lastCompletedDate.isBefore(referenceDate.minusDays(1))) {
            current = 1;
            Set<LocalDate> completedSet = new HashSet<>(completedDates);
            LocalDate cursor = lastCompletedDate;
            while (completedSet.contains(cursor.minusDays(1))) {
                current++;
                cursor = cursor.minusDays(1);
            }
        }

        return new StreakSnapshot(
                current,
                longest,
                lastCompletedDate,
                buildRecentDays(referenceDate, new HashSet<>(completedDates))
        );
    }

    private List<PrayerRecentDayResponse> buildRecentDays(LocalDate referenceDate, Set<LocalDate> completedDates) {
        List<PrayerRecentDayResponse> recentDays = new ArrayList<>();
        for (int daysBack = RECENT_DAY_WINDOW - 1; daysBack >= 0; daysBack--) {
            LocalDate day = referenceDate.minusDays(daysBack);
            recentDays.add(new PrayerRecentDayResponse(day, completedDates.contains(day)));
        }
        return recentDays;
    }

    private record StreakSnapshot(
            int currentStreak,
            int longestStreak,
            LocalDate lastCompletedDate,
            List<PrayerRecentDayResponse> recentDays
    ) {
    }
}
