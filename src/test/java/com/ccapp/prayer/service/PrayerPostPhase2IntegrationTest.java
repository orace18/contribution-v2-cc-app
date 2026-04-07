package com.ccapp.prayer.service;

import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import com.ccapp.prayer.dto.AdminCreatePrayerPostRequest;
import com.ccapp.prayer.dto.AdminPrayerPostResponse;
import com.ccapp.prayer.dto.CreatePrayerRequest;
import com.ccapp.prayer.dto.PrayerPostResponse;
import com.ccapp.prayer.dto.UpdatePrayerRequest;
import com.ccapp.prayer.repository.PrayerPostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        PrayerAdminPostService.class,
        PrayerPostMapper.class,
        TestJacksonAndCachingConfig.class
})
class PrayerPostPhase2IntegrationTest {

    @Autowired
    private PrayerMobileService prayerMobileService;
    @Autowired
    private PrayerAdminPostService prayerAdminPostService;
    @Autowired
    private PrayerPostRepository prayerPostRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private CircleMemberRepository circleMemberRepository;

    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private PrayerEngagementService prayerEngagementService;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPost_shouldPublishPrayerRequestWithCommunityCountrySnapshot() {
        UUID accountHolderId = createAccountHolder("Esther", "Doe", "BJ");
        UUID actorUserId = UUID.randomUUID();

        PrayerPostResponse response = prayerMobileService.createPrayerRequest(
                actorUserId,
                accountHolderId,
                new CreatePrayerRequest(UUID.randomUUID(), "Pray for my new business launch.", PrayerVisibilityScope.COMMUNITY, List.of())
        );

        assertEquals(PrayerPostType.REQUEST, response.postType());
        assertEquals(PrayerPostLifecycleStatus.PUBLISHED, response.lifecycleStatus());
        assertEquals(PrayerVisibilityScope.COMMUNITY, response.visibility());
        assertEquals("BJ", response.scopeContext().audienceCountryCode());
        assertNotNull(response.publishedAt());
    }

    @Test
    void readFeed_shouldApplyVisibilityRulesAndNearestScopeOrdering() {
        UUID viewerId = createAccountHolder("Viewer", "One", "BJ");
        UUID sameCircleUserId = createAccountHolder("Circle", "Peer", "BJ");
        UUID sameCountryUserId = createAccountHolder("Country", "Peer", "BJ");
        UUID otherCountryUserId = createAccountHolder("Remote", "Peer", "TG");
        Circle circle = createCircleWithMembers(viewerId, sameCircleUserId);

        PrayerPost privateMine = savePublishedRequest(viewerId, PrayerVisibilityScope.PRIVATE, "private", null, null, null, OffsetDateTime.now().minusMinutes(1));
        PrayerPost circlePost = savePublishedRequest(sameCircleUserId, PrayerVisibilityScope.CIRCLE, "circle", circle.getId(), null, null, OffsetDateTime.now().minusMinutes(2));
        PrayerPost communityPost = savePublishedRequest(sameCountryUserId, PrayerVisibilityScope.COMMUNITY, "community", null, null, "BJ", OffsetDateTime.now().minusMinutes(3));
        PrayerPost globalPost = savePublishedRequest(sameCountryUserId, PrayerVisibilityScope.GLOBAL, "global", null, null, null, OffsetDateTime.now().minusMinutes(4));
        PrayerPost officialGlobal = savePublishedOfficial("official", PrayerVisibilityScope.GLOBAL, OffsetDateTime.now().minusMinutes(5));
        savePublishedRequest(otherCountryUserId, PrayerVisibilityScope.COMMUNITY, "remote community", null, null, "TG", OffsetDateTime.now().minusMinutes(1));
        savePublishedRequest(otherCountryUserId, PrayerVisibilityScope.PRIVATE, "foreign private", null, null, null, OffsetDateTime.now().minusMinutes(1));

        List<UUID> ids = prayerMobileService.getPrayerFeed(viewerId, viewerId, 0, 20, true).items().stream()
                .map(PrayerPostResponse::id)
                .toList();

        assertEquals(List.of(
                privateMine.getId(),
                circlePost.getId(),
                communityPost.getId(),
                globalPost.getId(),
                officialGlobal.getId()
        ), ids);
    }

    @Test
    void readDetail_shouldReturnVisiblePostAndHideInvisiblePost() {
        UUID viewerId = createAccountHolder("Viewer", "One", "BJ");
        UUID otherCountryUserId = createAccountHolder("Remote", "Peer", "TG");
        PrayerPost visible = savePublishedRequest(viewerId, PrayerVisibilityScope.GLOBAL, "visible", null, null, null, OffsetDateTime.now());
        PrayerPost invisible = savePublishedRequest(otherCountryUserId, PrayerVisibilityScope.COMMUNITY, "hidden", null, null, "TG", OffsetDateTime.now());

        PrayerPostResponse visibleResponse = prayerMobileService.getPrayerPost(viewerId, viewerId, visible.getId());
        assertEquals(visible.getId(), visibleResponse.id());

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> prayerMobileService.getPrayerPost(viewerId, viewerId, invisible.getId())
        );
        assertEquals("PRAYER_POST_NOT_FOUND", exception.getMessage());
    }

    @Test
    void updateOwnPost_shouldPersistChanges() {
        UUID ownerId = createAccountHolder("Owner", "One", "BJ");
        PrayerPost post = savePublishedRequest(ownerId, PrayerVisibilityScope.COMMUNITY, "before", null, null, "BJ", OffsetDateTime.now());

        PrayerPostResponse updated = prayerMobileService.updatePrayerRequest(
                ownerId,
                ownerId,
                post.getId(),
                new UpdatePrayerRequest("after", PrayerVisibilityScope.GLOBAL, List.of())
        );

        assertEquals("after", updated.content());
        assertEquals(PrayerVisibilityScope.GLOBAL, updated.visibility());
        PrayerPost persisted = prayerPostRepository.findById(post.getId()).orElseThrow();
        assertEquals(PrayerVisibilityScope.GLOBAL, persisted.getVisibility());
        assertEquals(null, persisted.getAudienceCountryCode());
    }

    @Test
    void forbiddenUpdateOnForeignPost_shouldReturnAccessDenied() {
        UUID ownerId = createAccountHolder("Owner", "One", "BJ");
        UUID viewerId = createAccountHolder("Viewer", "Two", "BJ");
        PrayerPost post = savePublishedRequest(ownerId, PrayerVisibilityScope.GLOBAL, "before", null, null, null, OffsetDateTime.now());

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> prayerMobileService.updatePrayerRequest(
                        viewerId,
                        viewerId,
                        post.getId(),
                        new UpdatePrayerRequest("after", PrayerVisibilityScope.GLOBAL, List.of())
                )
        );
        assertEquals("PRAYER_POST_UPDATE_FORBIDDEN", exception.getMessage());
    }

    @Test
    void statusTransitionWhereAllowed_andOfficialPostSecurity_shouldBeEnforced() {
        UUID adminUserId = UUID.randomUUID();
        setAdminSecurity(adminUserId, AdminRole.ROLE_SUPPORT, "BJ");

        AccessDeniedException denied = assertThrows(
                AccessDeniedException.class,
                () -> prayerAdminPostService.createPost(
                        adminUserId,
                        new AdminCreatePrayerPostRequest(
                                "Official prayer",
                                "Pray with faith",
                                PrayerOfficialVisibilityScope.GLOBAL,
                                null,
                                null,
                                List.of(),
                                null
                        )
                )
        );
        assertEquals("PRAYER_OFFICIAL_POST_WRITE_FORBIDDEN", denied.getMessage());

        setAdminSecurity(adminUserId, AdminRole.ROLE_SUPER_ADMIN, "BJ");
        AdminPrayerPostResponse created = prayerAdminPostService.createPost(
                adminUserId,
                new AdminCreatePrayerPostRequest(
                        "Official prayer",
                        "Pray with faith",
                        PrayerOfficialVisibilityScope.COMMUNITY,
                        "BJ",
                        null,
                        List.of(),
                        null
                )
        );
        assertEquals(PrayerPostLifecycleStatus.DRAFT, created.lifecycleStatus());

        AdminPrayerPostResponse published = prayerAdminPostService.publishPost(adminUserId, created.id());
        assertEquals(PrayerPostLifecycleStatus.PUBLISHED, published.lifecycleStatus());
        assertNotNull(published.publishedAt());

        AdminPrayerPostResponse archived = prayerAdminPostService.archivePost(adminUserId, created.id());
        assertEquals(PrayerPostLifecycleStatus.ARCHIVED, archived.lifecycleStatus());
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

    private Circle createCircleWithMembers(UUID... memberIds) {
        Circle circle = circleRepository.save(Circle.builder()
                .name("Prayer Circle")
                .coachId(UUID.randomUUID())
                .countryCode("BJ")
                .status(CircleStatus.ACTIVE)
                .build());
        for (UUID memberId : memberIds) {
            circleMemberRepository.save(CircleMember.builder()
                    .circle(circle)
                    .accountHolderId(memberId)
                    .role(CircleMemberRole.MEMBER)
                    .financialMember(true)
                    .joinedAt(OffsetDateTime.now())
                    .build());
        }
        return circle;
    }

    private PrayerPost savePublishedRequest(UUID authorId,
                                            PrayerVisibilityScope visibility,
                                            String content,
                                            UUID circleId,
                                            String churchRef,
                                            String countryCode,
                                            OffsetDateTime publishedAt) {
        return prayerPostRepository.save(PrayerPost.builder()
                .postType(PrayerPostType.REQUEST)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .authorAccountHolderId(authorId)
                .content(content)
                .visibility(visibility)
                .audienceCircleId(circleId)
                .audienceChurchRef(churchRef)
                .audienceCountryCode(countryCode)
                .publishedAt(publishedAt)
                .build());
    }

    private PrayerPost savePublishedOfficial(String content,
                                             PrayerVisibilityScope visibility,
                                             OffsetDateTime publishedAt) {
        return prayerPostRepository.save(PrayerPost.builder()
                .postType(PrayerPostType.OFFICIAL)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .createdByAdminUserId(UUID.randomUUID())
                .title("Official")
                .content(content)
                .visibility(visibility)
                .publishedAt(publishedAt)
                .build());
    }

    private void setAdminSecurity(UUID adminUserId, AdminRole role, String countryCode) {
        AdminPrincipal principal = AdminPrincipal.builder()
                .adminUserId(adminUserId)
                .email("admin@ccapp.io")
                .role(role)
                .countryCode(countryCode)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(role.name()))
                )
        );
    }
}
