package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerAmen;
import com.ccapp.prayer.domain.entity.PrayerComment;
import com.ccapp.prayer.domain.entity.PrayerDailyCompletion;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerDailyMediaRef;
import com.ccapp.prayer.domain.entity.PrayerDailyMissionDefinition;
import com.ccapp.prayer.domain.entity.PrayerEngagementDailyCheckin;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.entity.PrayerPostMediaRef;
import com.ccapp.prayer.domain.entity.PrayerSupport;
import com.ccapp.prayer.domain.entity.PrayerUserDailyMission;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionStatus;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.domain.enums.PrayerMediaUsageType;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerSupportType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PrayerPersistenceIntegrationTest {

    @Autowired
    private PrayerPostRepository prayerPostRepository;
    @Autowired
    private PrayerPostMediaRefRepository prayerPostMediaRefRepository;
    @Autowired
    private PrayerAmenRepository prayerAmenRepository;
    @Autowired
    private PrayerSupportRepository prayerSupportRepository;
    @Autowired
    private PrayerCommentRepository prayerCommentRepository;
    @Autowired
    private PrayerDailyContentRepository prayerDailyContentRepository;
    @Autowired
    private PrayerDailyMediaRefRepository prayerDailyMediaRefRepository;
    @Autowired
    private PrayerDailyCompletionRepository prayerDailyCompletionRepository;
    @Autowired
    private PrayerEngagementDailyCheckinRepository prayerEngagementDailyCheckinRepository;
    @Autowired
    private PrayerDailyMissionDefinitionRepository prayerDailyMissionDefinitionRepository;
    @Autowired
    private PrayerUserDailyMissionRepository prayerUserDailyMissionRepository;

    @Test
    void shouldPersistPrayerPhase1MvpAggregates() {
        UUID authorAccountHolderId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        LocalDate prayerDate = LocalDate.of(2026, 4, 7);

        PrayerPost post = prayerPostRepository.save(PrayerPost.builder()
                .postType(PrayerPostType.REQUEST)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .authorAccountHolderId(authorAccountHolderId)
                .actorUserId(actorUserId)
                .content("Please pray for wisdom in business decisions.")
                .visibility(PrayerVisibilityScope.COMMUNITY)
                .audienceCountryCode("BJ")
                .clientRequestId(UUID.randomUUID())
                .build());

        prayerPostMediaRefRepository.save(PrayerPostMediaRef.builder()
                .postId(post.getId())
                .mediaFileId(UUID.randomUUID())
                .usageType(PrayerMediaUsageType.COVER)
                .sortOrder(0)
                .build());

        prayerAmenRepository.save(PrayerAmen.builder()
                .postId(post.getId())
                .accountHolderId(authorAccountHolderId)
                .actorUserId(actorUserId)
                .build());

        prayerSupportRepository.save(PrayerSupport.builder()
                .postId(post.getId())
                .accountHolderId(authorAccountHolderId)
                .actorUserId(actorUserId)
                .supportType(PrayerSupportType.PRAYED)
                .build());

        prayerCommentRepository.save(PrayerComment.builder()
                .postId(post.getId())
                .accountHolderId(authorAccountHolderId)
                .actorUserId(actorUserId)
                .clientRequestId(UUID.randomUUID())
                .content("We are standing with you in prayer.")
                .build());

        PrayerDailyContent dailyContent = prayerDailyContentRepository.save(PrayerDailyContent.builder()
                .prayerDate(prayerDate)
                .title("Prayer for faithful stewardship")
                .scriptureReference("Proverbs 16:3")
                .content("Lord, guide every plan and every partnership.")
                .status(PrayerDailyPrayerStatus.PUBLISHED)
                .createdByAdminUserId(UUID.randomUUID())
                .build());

        prayerDailyMediaRefRepository.save(PrayerDailyMediaRef.builder()
                .dailyPrayerId(dailyContent.getId())
                .mediaFileId(UUID.randomUUID())
                .usageType(PrayerMediaUsageType.INLINE)
                .sortOrder(0)
                .build());

        prayerDailyCompletionRepository.save(PrayerDailyCompletion.builder()
                .dailyPrayerId(dailyContent.getId())
                .accountHolderId(authorAccountHolderId)
                .actorUserId(actorUserId)
                .prayerDate(prayerDate)
                .timezone("Africa/Porto-Novo")
                .responseType(PrayerDailyPrayerResponseType.COMPLETED)
                .build());

        prayerEngagementDailyCheckinRepository.save(PrayerEngagementDailyCheckin.builder()
                .accountHolderId(authorAccountHolderId)
                .actorUserId(actorUserId)
                .localDate(prayerDate)
                .timezone("Africa/Porto-Novo")
                .responseType(PrayerDailyPrayerResponseType.COMPLETED)
                .sourceReferenceId(dailyContent.getId())
                .build());

        prayerDailyMissionDefinitionRepository.save(PrayerDailyMissionDefinition.builder()
                .missionCode(PrayerDailyMissionCode.READ_DAILY_PRAYER)
                .title("Read the daily prayer")
                .description("Open and complete the daily prayer once.")
                .targetValue(1)
                .active(true)
                .sortOrder(10)
                .build());

        prayerUserDailyMissionRepository.save(PrayerUserDailyMission.builder()
                .accountHolderId(authorAccountHolderId)
                .missionDate(prayerDate)
                .missionCode(PrayerDailyMissionCode.READ_DAILY_PRAYER)
                .status(PrayerDailyMissionStatus.COMPLETED)
                .progressValue(1)
                .targetValue(1)
                .build());

        assertEquals(1L, prayerAmenRepository.countByPostId(post.getId()));
        assertTrue(prayerAmenRepository.existsByPostIdAndAccountHolderId(post.getId(), authorAccountHolderId));
        assertEquals(1L, prayerSupportRepository.countByPostId(post.getId()));
        assertTrue(prayerSupportRepository.existsByPostIdAndAccountHolderId(post.getId(), authorAccountHolderId));
        assertEquals(1, prayerCommentRepository.findByPostIdOrderByCreatedAtAsc(post.getId()).size());
        assertEquals(1, prayerPostMediaRefRepository.findByPostIdOrderBySortOrderAsc(post.getId()).size());
        assertTrue(prayerDailyContentRepository.findByPrayerDateAndStatus(prayerDate, PrayerDailyPrayerStatus.PUBLISHED).isPresent());
        assertEquals(1, prayerDailyMediaRefRepository.findByDailyPrayerIdOrderBySortOrderAsc(dailyContent.getId()).size());
        assertTrue(prayerDailyCompletionRepository.findByAccountHolderIdAndPrayerDate(authorAccountHolderId, prayerDate).isPresent());
        assertEquals(1, prayerDailyCompletionRepository.findByAccountHolderIdOrderByPrayerDateDesc(authorAccountHolderId).size());
        assertTrue(prayerEngagementDailyCheckinRepository.findByAccountHolderIdAndLocalDate(authorAccountHolderId, prayerDate).isPresent());
        assertTrue(prayerDailyMissionDefinitionRepository.findByMissionCode(PrayerDailyMissionCode.READ_DAILY_PRAYER).isPresent());
        List<PrayerUserDailyMission> missions = prayerUserDailyMissionRepository
                .findByAccountHolderIdAndMissionDateOrderByMissionCodeAsc(authorAccountHolderId, prayerDate);
        assertEquals(1, missions.size());
        assertEquals(PrayerDailyMissionStatus.COMPLETED, missions.getFirst().getStatus());
    }
}
