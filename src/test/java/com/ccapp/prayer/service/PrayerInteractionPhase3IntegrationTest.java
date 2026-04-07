package com.ccapp.prayer.service;

import com.ccapp.circles.repository.CircleMemberInteractionRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.media.storage.ObjectStorageService;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.enums.PrayerInteractionType;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import com.ccapp.prayer.dto.CreatePrayerCommentRequest;
import com.ccapp.prayer.dto.PrayerCommentResponse;
import com.ccapp.prayer.dto.PrayerInteractionResponse;
import com.ccapp.prayer.dto.PrayerPostResponse;
import com.ccapp.prayer.repository.PrayerAmenRepository;
import com.ccapp.prayer.repository.PrayerCommentRepository;
import com.ccapp.prayer.repository.PrayerPostRepository;
import com.ccapp.prayer.repository.PrayerSupportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        PrayerMobileService.class,
        PrayerPostMediaService.class,
        PrayerPostMapper.class,
        TestJacksonAndCachingConfig.class
})
class PrayerInteractionPhase3IntegrationTest {

    @Autowired
    private PrayerMobileService prayerMobileService;
    @Autowired
    private PrayerPostRepository prayerPostRepository;
    @Autowired
    private PrayerAmenRepository prayerAmenRepository;
    @Autowired
    private PrayerSupportRepository prayerSupportRepository;
    @Autowired
    private PrayerCommentRepository prayerCommentRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private CircleMemberInteractionRepository circleMemberInteractionRepository;

    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private PrayerEngagementService prayerEngagementService;
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
    }

    @Test
    void amenIdempotency_shouldAvoidDuplicateRowsAndCounts() {
        UUID ownerId = createAccountHolder("Owner", "One", "BJ");
        UUID viewerId = createAccountHolder("Viewer", "Amen", "BJ");
        PrayerPost post = savePublishedRequest(ownerId, PrayerVisibilityScope.GLOBAL, "Pray for my company", null);

        PrayerInteractionResponse first = prayerMobileService.recordAmen(viewerId, viewerId, post.getId());
        PrayerInteractionResponse second = prayerMobileService.recordAmen(viewerId, viewerId, post.getId());

        assertEquals(PrayerInteractionType.AMEN, first.interactionType());
        assertTrue(first.created());
        assertEquals(1, first.totalCount());
        assertTrue(first.viewerState().hasAmened());
        assertFalse(second.created());
        assertEquals(1, second.totalCount());
        assertEquals(first.recordedAt(), second.recordedAt());
        assertEquals(1L, prayerAmenRepository.countByPostId(post.getId()));
        assertEquals(1, prayerPostRepository.findById(post.getId()).orElseThrow().getAmenCount());
    }

    @Test
    void prayIdempotency_shouldAvoidDuplicateRowsAndCounts_withoutCircleCollision() {
        UUID ownerId = createAccountHolder("Owner", "One", "BJ");
        UUID supporterId = createAccountHolder("Supporter", "One", "BJ");
        PrayerPost post = savePublishedRequest(ownerId, PrayerVisibilityScope.GLOBAL, "Please pray for this business", null);

        PrayerInteractionResponse first = prayerMobileService.recordSupport(supporterId, supporterId, post.getId());
        PrayerInteractionResponse second = prayerMobileService.recordSupport(supporterId, supporterId, post.getId());

        assertEquals(PrayerInteractionType.SUPPORT, first.interactionType());
        assertTrue(first.created());
        assertEquals(1, first.totalCount());
        assertTrue(first.viewerState().hasSupported());
        assertFalse(second.created());
        assertEquals(1, second.totalCount());
        assertEquals(first.recordedAt(), second.recordedAt());
        assertEquals(1L, prayerSupportRepository.countByPostId(post.getId()));
        assertEquals(1, prayerPostRepository.findById(post.getId()).orElseThrow().getSupportCount());
        assertEquals(0L, circleMemberInteractionRepository.count());
        verify(prayerNotificationPublisher).publishSupportReceived(
                org.mockito.ArgumentMatchers.eq(supporterId),
                org.mockito.ArgumentMatchers.eq("Supporter One"),
                argThat(persisted -> persisted != null && post.getId().equals(persisted.getId()))
        );
    }

    @Test
    void commentCreation_shouldPersistAndRemainIdempotentOnClientRetry() {
        UUID ownerId = createAccountHolder("Owner", "One", "BJ");
        UUID commenterId = createAccountHolder("Commenter", "One", "BJ");
        PrayerPost post = savePublishedRequest(ownerId, PrayerVisibilityScope.GLOBAL, "Need encouragement", null);
        UUID clientRequestId = UUID.randomUUID();

        PrayerCommentResponse first = prayerMobileService.createPrayerComment(
                commenterId,
                commenterId,
                post.getId(),
                new CreatePrayerCommentRequest(clientRequestId, "  Stay strong in faith.  ")
        );
        PrayerCommentResponse second = prayerMobileService.createPrayerComment(
                commenterId,
                commenterId,
                post.getId(),
                new CreatePrayerCommentRequest(clientRequestId, "Stay strong in faith.")
        );
        List<PrayerCommentResponse> comments = prayerMobileService.listPrayerComments(commenterId, commenterId, post.getId(), 0, 20).items();

        assertEquals(first.id(), second.id());
        assertEquals("Stay strong in faith.", first.content());
        assertEquals(1, comments.size());
        assertEquals(first.id(), comments.get(0).id());
        assertEquals(commenterId, comments.get(0).author().accountHolderId());
        assertEquals(1L, prayerCommentRepository.countByPostId(post.getId()));
        assertEquals(1, prayerPostRepository.findById(post.getId()).orElseThrow().getCommentCount());
        verify(prayerNotificationPublisher).publishCommentReceived(
                org.mockito.ArgumentMatchers.eq(commenterId),
                org.mockito.ArgumentMatchers.eq("Commenter One"),
                argThat(persisted -> persisted != null && post.getId().equals(persisted.getId()))
        );
    }

    @Test
    void commentVisibility_shouldRequirePostAccess() {
        UUID ownerId = createAccountHolder("Owner", "One", "TG");
        UUID viewerId = createAccountHolder("Viewer", "One", "BJ");
        PrayerPost post = savePublishedRequest(ownerId, PrayerVisibilityScope.COMMUNITY, "Hidden from this viewer", "TG");

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> prayerMobileService.listPrayerComments(viewerId, viewerId, post.getId(), 0, 20)
        );

        assertEquals("PRAYER_POST_NOT_FOUND", exception.getMessage());
    }

    @Test
    void unauthorizedInteractionBlocked_shouldHideInvisiblePost() {
        UUID ownerId = createAccountHolder("Owner", "One", "TG");
        UUID viewerId = createAccountHolder("Viewer", "One", "BJ");
        PrayerPost post = savePublishedRequest(ownerId, PrayerVisibilityScope.COMMUNITY, "Not visible here", "TG");

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> prayerMobileService.recordAmen(viewerId, viewerId, post.getId())
        );

        assertEquals("PRAYER_POST_NOT_FOUND", exception.getMessage());
    }

    @Test
    void aggregateCountersCorrectness_shouldBeVisibleOnPostDetail() {
        UUID ownerId = createAccountHolder("Owner", "One", "BJ");
        UUID amenUserId = createAccountHolder("Amen", "User", "BJ");
        UUID supportUserId = createAccountHolder("Support", "User", "BJ");
        UUID commentUserId = createAccountHolder("Comment", "User", "BJ");
        PrayerPost post = savePublishedRequest(ownerId, PrayerVisibilityScope.GLOBAL, "Need all kinds of support", null);

        prayerMobileService.recordAmen(amenUserId, amenUserId, post.getId());
        prayerMobileService.recordSupport(supportUserId, supportUserId, post.getId());
        prayerMobileService.createPrayerComment(
                commentUserId,
                commentUserId,
                post.getId(),
                new CreatePrayerCommentRequest(UUID.randomUUID(), "We are praying with you.")
        );

        PrayerPostResponse detail = prayerMobileService.getPrayerPost(ownerId, ownerId, post.getId());

        assertEquals(1, detail.stats().amenCount());
        assertEquals(1, detail.stats().supportCount());
        assertEquals(1, detail.stats().commentCount());
        assertNotNull(detail.viewerState());
    }

    @Test
    void supportOnOfficialPost_shouldBeRejected() {
        UUID viewerId = createAccountHolder("Viewer", "One", "BJ");
        PrayerPost post = savePublishedOfficial("Official prayer", PrayerVisibilityScope.GLOBAL);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> prayerMobileService.recordSupport(viewerId, viewerId, post.getId())
        );

        assertEquals("PRAYER_SUPPORT_NOT_ALLOWED", exception.getMessage());
        verify(prayerNotificationPublisher, never()).publishSupportReceived(any(), any(), any());
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

    private PrayerPost savePublishedRequest(UUID authorId,
                                            PrayerVisibilityScope visibility,
                                            String content,
                                            String countryCode) {
        return prayerPostRepository.save(PrayerPost.builder()
                .postType(PrayerPostType.REQUEST)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .authorAccountHolderId(authorId)
                .content(content)
                .visibility(visibility)
                .audienceCountryCode(countryCode)
                .publishedAt(OffsetDateTime.now())
                .build());
    }

    private PrayerPost savePublishedOfficial(String content, PrayerVisibilityScope visibility) {
        return prayerPostRepository.save(PrayerPost.builder()
                .postType(PrayerPostType.OFFICIAL)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .createdByAdminUserId(UUID.randomUUID())
                .title("Official")
                .content(content)
                .visibility(visibility)
                .publishedAt(OffsetDateTime.now())
                .build());
    }
}
