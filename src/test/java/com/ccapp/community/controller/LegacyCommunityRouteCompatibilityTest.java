package com.ccapp.community.controller;

import com.ccapp.admin.controller.AdminCommunityController;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.community.dto.CommunityFeedResponse;
import com.ccapp.community.dto.CommunityPostResponse;
import com.ccapp.community.dto.FeatureCommunityPostRequest;
import com.ccapp.community.service.CommunityAdminService;
import com.ccapp.community.service.CommunityEngagementService;
import com.ccapp.community.service.CommunityFeedService;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import com.ccapp.prayer.dto.PrayerAuthorResponse;
import com.ccapp.prayer.dto.PrayerPostResponse;
import com.ccapp.prayer.dto.PrayerScopeContextResponse;
import com.ccapp.prayer.dto.PrayerStatsResponse;
import com.ccapp.prayer.dto.PrayerViewerStateResponse;
import com.ccapp.prayer.service.PrayerAdminPostService;
import com.ccapp.prayer.service.PrayerMobileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyCommunityRouteCompatibilityTest {

    @Mock
    private CommunityFeedService communityFeedService;
    @Mock
    private CommunityEngagementService communityEngagementService;
    @Mock
    private PrayerMobileService prayerMobileService;
    @Mock
    private CommunityAdminService communityAdminService;
    @Mock
    private PrayerAdminPostService prayerAdminPostService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void oldMobileCommunityFeed_shouldDelegateToPrayerFeedAndExposeDeprecationHeaders() {
        UUID viewerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedPrincipal.builder()
                        .userId(viewerId)
                        .accountHolderId(viewerId)
                        .build(),
                null,
                List.of()
        ));

        PrayerPostResponse official = new PrayerPostResponse(
                UUID.randomUUID(),
                PrayerPostType.OFFICIAL,
                PrayerPostLifecycleStatus.PUBLISHED,
                "Official title",
                "Official content",
                PrayerVisibilityScope.GLOBAL,
                new PrayerAuthorResponse(null, "Club Chretien", null, null),
                new PrayerScopeContextResponse(null, null, null),
                List.of(),
                null,
                new PrayerStatsResponse(0, 0, 0),
                new PrayerViewerStateResponse(false, false, true, false, true),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        PrayerPostResponse request = new PrayerPostResponse(
                UUID.randomUUID(),
                PrayerPostType.REQUEST,
                PrayerPostLifecycleStatus.PUBLISHED,
                null,
                "Request content",
                PrayerVisibilityScope.GLOBAL,
                new PrayerAuthorResponse(viewerId, "Viewer", null, null),
                new PrayerScopeContextResponse(null, null, null),
                List.of(),
                null,
                new PrayerStatsResponse(2, 0, 1),
                new PrayerViewerStateResponse(true, false, true, false, true),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(prayerMobileService.getPrayerFeed(viewerId, viewerId, 0, 20, true))
                .thenReturn(new PageResponse<>(List.of(official, request), 0, 20, 2, 1, false));

        MobileCommunityController controller = new MobileCommunityController(
                communityFeedService,
                communityEngagementService,
                prayerMobileService
        );

        ResponseEntity<CommonResponse<CommunityFeedResponse>> response = controller.feed();

        assertEquals("true", response.getHeaders().getFirst("X-Deprecated-Route"));
        assertEquals("/api/mobile/prayers/feed", response.getHeaders().getFirst("X-Replacement-Route"));
        assertEquals(1, response.getBody().data().featuredPosts().size());
        assertEquals(1, response.getBody().data().posts().size());
    }

    @Test
    void oldAdminFeatureRoute_shouldDelegateToPrayerPublishAndExposeDeprecationHeaders() {
        UUID adminUserId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AdminPrincipal.builder()
                        .adminUserId(adminUserId)
                        .role(AdminRole.ROLE_SUPER_ADMIN)
                        .email("admin@ccapp.io")
                        .build(),
                null,
                List.of(new SimpleGrantedAuthority(AdminRole.ROLE_SUPER_ADMIN.name()))
        ));

        when(prayerAdminPostService.publishPost(adminUserId, postId)).thenReturn(
                new com.ccapp.prayer.dto.AdminPrayerPostResponse(
                        postId,
                        PrayerPostType.OFFICIAL,
                        PrayerPostLifecycleStatus.PUBLISHED,
                        "Official title",
                        "Official content",
                        com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope.GLOBAL,
                        null,
                        null,
                        List.of(),
                        null,
                        adminUserId,
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )
        );

        AdminCommunityController controller = new AdminCommunityController(communityAdminService, prayerAdminPostService);
        ResponseEntity<CommonResponse<CommunityPostResponse>> response = controller.feature(postId, new FeatureCommunityPostRequest(1));

        assertEquals("true", response.getHeaders().getFirst("X-Deprecated-Route"));
        assertTrue(response.getHeaders().getFirst("X-Replacement-Route").endsWith("/publish"));
        assertTrue(response.getBody().data().featured());
    }
}
