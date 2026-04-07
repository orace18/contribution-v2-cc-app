package com.ccapp.prayer.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.prayer.domain.entity.PrayerDailyCompletion;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerDailyMissionDefinition;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.entity.PrayerUserDailyMission;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionStatus;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import com.ccapp.prayer.dto.CreatePrayerCommentRequest;
import com.ccapp.prayer.dto.DailyPrayerCompletionResponse;
import com.ccapp.prayer.dto.DailyPrayerResponse;
import com.ccapp.prayer.dto.PrayerDashboardResponse;
import com.ccapp.prayer.dto.PrayerMissionProgressResponse;
import com.ccapp.prayer.repository.PrayerDailyCompletionRepository;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import com.ccapp.prayer.repository.PrayerDailyMissionDefinitionRepository;
import com.ccapp.prayer.repository.PrayerPostRepository;
import com.ccapp.prayer.repository.PrayerUserDailyMissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        PrayerMobileService.class,
        PrayerEngagementService.class,
        PrayerPostMediaService.class,
        PrayerPostMapper.class,
        PrayerDailyMapper.class,
        PrayerTimezoneResolver.class,
        TestJacksonAndCachingConfig.class
})
class PrayerDailyPhase4IntegrationTest {

    private static final String TEST_TIMEZONE = "Africa/Porto-Novo";

    @Autowired
    private PrayerMobileService prayerMobileService;
    @Autowired
    private PrayerDailyContentRepository prayerDailyContentRepository;
    @Autowired
    private PrayerDailyCompletionRepository prayerDailyCompletionRepository;
    @Autowired
    private PrayerDailyMissionDefinitionRepository prayerDailyMissionDefinitionRepository;
    @Autowired
    private PrayerUserDailyMissionRepository prayerUserDailyMissionRepository;
    @Autowired
    private PrayerPostRepository prayerPostRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private PrayerNotificationPublisher prayerNotificationPublisher;
    @MockBean
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
        seedMissionDefinitions();
    }

    @Test
    void getTodaysDailyPrayer_andRespondWithPrayedThenAmen_shouldBeIdempotentForSameDay() {
        UUID accountHolderId = createAccountHolder("Grace", "Owner", "BJ");
        LocalDate today = LocalDate.now(ZoneId.of(TEST_TIMEZONE));
        savePublishedDailyPrayer(today, "Priere du jour");

        DailyPrayerResponse current = prayerMobileService.getCurrentDailyPrayer(accountHolderId, accountHolderId, TEST_TIMEZONE);
        assertEquals(today, current.prayerDate());
        assertFalse(current.completedToday());
        assertEquals(null, current.todayResponseType());

        DailyPrayerCompletionResponse first = prayerMobileService.completeCurrentDailyPrayer(
                accountHolderId,
                accountHolderId,
                TEST_TIMEZONE,
                PrayerDailyPrayerResponseType.PRAYED
        );
        DailyPrayerCompletionResponse second = prayerMobileService.completeCurrentDailyPrayer(
                accountHolderId,
                accountHolderId,
                TEST_TIMEZONE,
                PrayerDailyPrayerResponseType.AMEN
        );
        DailyPrayerCompletionResponse third = prayerMobileService.completeCurrentDailyPrayer(
                accountHolderId,
                accountHolderId,
                TEST_TIMEZONE,
                PrayerDailyPrayerResponseType.AMEN
        );

        assertEquals(1L, prayerDailyCompletionRepository.countByAccountHolderId(accountHolderId));
        assertEquals(PrayerDailyPrayerResponseType.PRAYED, first.responseType());
        assertEquals(PrayerDailyPrayerResponseType.AMEN, second.responseType());
        assertEquals(second.completedAt(), third.completedAt());
        assertEquals(PrayerDailyPrayerResponseType.AMEN, third.responseType());
        assertEquals(1, second.currentStreak());
        assertEquals(1, second.longestStreak());
        assertTrue(second.completedMissionCodes().contains(PrayerDailyMissionCode.READ_DAILY_PRAYER));

        DailyPrayerResponse refreshed = prayerMobileService.getCurrentDailyPrayer(accountHolderId, accountHolderId, TEST_TIMEZONE);
        assertTrue(refreshed.completedToday());
        assertEquals(PrayerDailyPrayerResponseType.AMEN, refreshed.todayResponseType());
    }

    @Test
    void streakShouldIncrementAcrossConsecutiveDays() {
        UUID accountHolderId = createAccountHolder("Deborah", "Builder", "BJ");
        LocalDate today = LocalDate.now(ZoneId.of(TEST_TIMEZONE));
        savePublishedDailyPrayer(today.minusDays(2), "Day -2");
        savePublishedDailyPrayer(today.minusDays(1), "Day -1");
        savePublishedDailyPrayer(today, "Day 0");

        saveCompletion(accountHolderId, today.minusDays(2), PrayerDailyPrayerResponseType.PRAYED);
        saveCompletion(accountHolderId, today.minusDays(1), PrayerDailyPrayerResponseType.PRAYED);

        DailyPrayerCompletionResponse completion = prayerMobileService.completeCurrentDailyPrayer(
                accountHolderId,
                accountHolderId,
                TEST_TIMEZONE,
                PrayerDailyPrayerResponseType.PRAYED
        );

        assertEquals(3, completion.currentStreak());
        assertEquals(3, completion.longestStreak());

        PrayerDashboardResponse dashboard = prayerMobileService.getPrayerDashboard(accountHolderId, accountHolderId, TEST_TIMEZONE);
        assertEquals(3, dashboard.streak().currentStreak());
        assertEquals(today, dashboard.streak().lastCompletedDate());
    }

    @Test
    void streakShouldResetAfterGap() {
        UUID accountHolderId = createAccountHolder("Naomi", "Trader", "BJ");
        LocalDate today = LocalDate.now(ZoneId.of(TEST_TIMEZONE));

        saveCompletion(accountHolderId, today.minusDays(5), PrayerDailyPrayerResponseType.PRAYED);
        saveCompletion(accountHolderId, today.minusDays(4), PrayerDailyPrayerResponseType.AMEN);
        saveCompletion(accountHolderId, today.minusDays(2), PrayerDailyPrayerResponseType.PRAYED);

        PrayerDashboardResponse dashboard = prayerMobileService.getPrayerDashboard(accountHolderId, accountHolderId, TEST_TIMEZONE);

        assertEquals(0, dashboard.streak().currentStreak());
        assertEquals(2, dashboard.streak().longestStreak());
        assertEquals(today.minusDays(2), dashboard.streak().lastCompletedDate());
    }

    @Test
    void dailyMissionProgressAndCompletionShouldUpdateWithoutOvercounting() {
        UUID ownerId = createAccountHolder("Owner", "One", "BJ");
        UUID actorId = createAccountHolder("Helper", "Two", "BJ");
        LocalDate today = LocalDate.now(ZoneId.of(TEST_TIMEZONE));
        savePublishedDailyPrayer(today, "Priere pour aujourd hui");
        PrayerPost requestPost = prayerPostRepository.save(PrayerPost.builder()
                .postType(PrayerPostType.REQUEST)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .authorAccountHolderId(ownerId)
                .content("Priez pour mon commerce.")
                .visibility(PrayerVisibilityScope.GLOBAL)
                .publishedAt(OffsetDateTime.now())
                .build());

        prayerMobileService.completeCurrentDailyPrayer(actorId, actorId, TEST_TIMEZONE, PrayerDailyPrayerResponseType.PRAYED);
        prayerMobileService.recordSupport(actorId, actorId, requestPost.getId());
        prayerMobileService.recordSupport(actorId, actorId, requestPost.getId());
        prayerMobileService.createPrayerComment(
                actorId,
                actorId,
                requestPost.getId(),
                new CreatePrayerCommentRequest(UUID.randomUUID(), "Nous prions avec toi.")
        );
        prayerMobileService.createPrayerComment(
                actorId,
                actorId,
                requestPost.getId(),
                new CreatePrayerCommentRequest(UUID.randomUUID(), "Le Seigneur te fortifie.")
        );

        PrayerDashboardResponse dashboard = prayerMobileService.getPrayerDashboard(actorId, actorId, TEST_TIMEZONE);
        Map<PrayerDailyMissionCode, PrayerMissionProgressResponse> missionsByCode = dashboard.missions().stream()
                .collect(Collectors.toMap(PrayerMissionProgressResponse::missionCode, Function.identity()));

        assertMissionCompleted(missionsByCode.get(PrayerDailyMissionCode.READ_DAILY_PRAYER));
        assertMissionCompleted(missionsByCode.get(PrayerDailyMissionCode.SUPPORT_ONE_REQUEST));
        assertMissionCompleted(missionsByCode.get(PrayerDailyMissionCode.COMMENT_ONE_POST));
        assertEquals(1, missionsByCode.get(PrayerDailyMissionCode.SUPPORT_ONE_REQUEST).progressValue());
        assertEquals(1, missionsByCode.get(PrayerDailyMissionCode.COMMENT_ONE_POST).progressValue());
        assertEquals(1, dashboard.engagementStats().supportGivenCount());
        assertEquals(2, dashboard.engagementStats().commentsWrittenCount());

        List<PrayerUserDailyMission> persisted = prayerUserDailyMissionRepository
                .findByAccountHolderIdAndMissionDateOrderByMissionCodeAsc(actorId, today);
        assertEquals(3, persisted.size());
        assertTrue(persisted.stream().allMatch(mission -> mission.getStatus() == PrayerDailyMissionStatus.COMPLETED));
    }

    @Test
    void getCurrentDailyPrayerShouldFailWhenNoPublishedPrayerExistsForLocalDate() {
        UUID accountHolderId = createAccountHolder("Esther", "Absent", "BJ");

        NotFoundException exception = org.junit.jupiter.api.Assertions.assertThrows(
                NotFoundException.class,
                () -> prayerMobileService.getCurrentDailyPrayer(accountHolderId, accountHolderId, TEST_TIMEZONE)
        );

        assertEquals("PRAYER_DAILY_NOT_FOUND", exception.getMessage());
    }

    private void assertMissionCompleted(PrayerMissionProgressResponse mission) {
        assertNotNull(mission);
        assertEquals(PrayerDailyMissionStatus.COMPLETED, mission.status());
        assertEquals(mission.targetValue(), mission.progressValue());
        assertNotNull(mission.completedAt());
    }

    private UUID createAccountHolder(String firstName, String lastName, String serviceCountryCode) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode(serviceCountryCode)
                .serviceCountryCode(serviceCountryCode)
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }

    private PrayerDailyContent savePublishedDailyPrayer(LocalDate prayerDate, String title) {
        return prayerDailyContentRepository.save(PrayerDailyContent.builder()
                .prayerDate(prayerDate)
                .title(title)
                .scriptureReference("Proverbs 16:3")
                .content("Seigneur, dirige mes pas dans mes affaires.")
                .status(PrayerDailyPrayerStatus.PUBLISHED)
                .createdByAdminUserId(UUID.randomUUID())
                .publishedAt(OffsetDateTime.now())
                .build());
    }

    private void saveCompletion(UUID accountHolderId, LocalDate prayerDate, PrayerDailyPrayerResponseType responseType) {
        PrayerDailyContent dailyPrayer = prayerDailyContentRepository.findByPrayerDate(prayerDate)
                .orElseGet(() -> savePublishedDailyPrayer(prayerDate, "Archive " + prayerDate));
        prayerDailyCompletionRepository.save(PrayerDailyCompletion.builder()
                .dailyPrayerId(dailyPrayer.getId())
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .prayerDate(prayerDate)
                .timezone(TEST_TIMEZONE)
                .responseType(responseType)
                .completedAt(OffsetDateTime.now().minusDays(Math.max(0, LocalDate.now(ZoneId.of(TEST_TIMEZONE)).toEpochDay() - prayerDate.toEpochDay())))
                .build());
    }

    private void seedMissionDefinitions() {
        if (prayerDailyMissionDefinitionRepository.count() > 0) {
            return;
        }
        prayerDailyMissionDefinitionRepository.saveAll(List.of(
                PrayerDailyMissionDefinition.builder()
                        .missionCode(PrayerDailyMissionCode.READ_DAILY_PRAYER)
                        .title("Lire la priere du jour")
                        .description("Termine la priere du jour.")
                        .targetValue(1)
                        .active(true)
                        .sortOrder(10)
                        .build(),
                PrayerDailyMissionDefinition.builder()
                        .missionCode(PrayerDailyMissionCode.SUPPORT_ONE_REQUEST)
                        .title("Soutenir une demande")
                        .description("Prie pour une demande visible.")
                        .targetValue(1)
                        .active(true)
                        .sortOrder(20)
                        .build(),
                PrayerDailyMissionDefinition.builder()
                        .missionCode(PrayerDailyMissionCode.COMMENT_ONE_POST)
                        .title("Encourager par un commentaire")
                        .description("Publie un commentaire d encouragement.")
                        .targetValue(1)
                        .active(true)
                        .sortOrder(30)
                        .build()
        ));
    }
}
