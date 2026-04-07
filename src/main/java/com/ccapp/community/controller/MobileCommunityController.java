package com.ccapp.community.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.community.dto.CommunityFeedResponse;
import com.ccapp.community.dto.CommunityPostResponse;
import com.ccapp.community.dto.PostCommentRequest;
import com.ccapp.community.dto.PostCommentResponse;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.dto.CreatePrayerCommentRequest;
import com.ccapp.prayer.dto.PrayerCommentResponse;
import com.ccapp.prayer.dto.PrayerPostResponse;
import com.ccapp.prayer.service.PrayerMobileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @deprecated Replaced by {@code com.ccapp.prayer.controller.mobile.PrayerMobileController}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
@RestController
@RequestMapping("/api/mobile/community")
@RequiredArgsConstructor
@Tag(name = "Mobile Community", description = "Community feed for mobile home screen")
@SecurityRequirement(name = "bearerAuth")
public class MobileCommunityController {

    private final PrayerMobileService prayerMobileService;

    @GetMapping("/feed")
    @Operation(summary = "Get mobile community feed")
    public ResponseEntity<CommonResponse<CommunityFeedResponse>> feed() {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PageResponse<PrayerPostResponse> prayerFeed = prayerMobileService.getPrayerFeed(
                principal.userId(),
                accountHolderId,
                0,
                20,
                true
        );
        CommunityFeedResponse response = new CommunityFeedResponse(
                prayerFeed.items().stream()
                        .filter(post -> post.postType() == PrayerPostType.OFFICIAL)
                        .map(this::toCommunityPost)
                        .toList(),
                prayerFeed.items().stream()
                        .filter(post -> post.postType() == PrayerPostType.REQUEST)
                        .map(this::toCommunityPost)
                        .toList()
        );
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/mobile/prayers/feed"))
                .body(CommonResponse.success("Community feed", response));
    }

    @PostMapping("/posts/{postId}/like")
    @Operation(summary = "Like a community post")
    public ResponseEntity<CommonResponse<Void>> likePost(@PathVariable UUID postId) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        prayerMobileService.recordAmen(principal.userId(), accountHolderId, postId);
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/mobile/prayers/posts/" + postId + "/amen"))
                .body(CommonResponse.success("Post liked", null));
    }

    @DeleteMapping("/posts/{postId}/like")
    @Operation(summary = "Unlike a community post")
    public ResponseEntity<CommonResponse<Void>> unlikePost(@PathVariable UUID postId) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        prayerMobileService.removeAmenCompatibility(principal.userId(), accountHolderId, postId);
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/mobile/prayers/posts/" + postId + "/amen"))
                .body(CommonResponse.success("Post unliked", null));
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "List comments for a community post")
    public ResponseEntity<CommonResponse<List<PostCommentResponse>>> listComments(@PathVariable UUID postId) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        List<PostCommentResponse> response = prayerMobileService.listPrayerComments(
                        principal.userId(),
                        accountHolderId,
                        postId,
                        0,
                        1000
                )
                .items()
                .stream()
                .map(this::toLegacyComment)
                .toList();
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/mobile/prayers/posts/" + postId + "/comments"))
                .body(CommonResponse.success("Post comments", response));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "Add a comment to a community post")
    public ResponseEntity<CommonResponse<PostCommentResponse>> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody PostCommentRequest request) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerCommentResponse response = prayerMobileService.createPrayerComment(
                principal.userId(),
                accountHolderId,
                postId,
                new CreatePrayerCommentRequest(null, request.content())
        );
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/mobile/prayers/posts/" + postId + "/comments"))
                .body(CommonResponse.success("Comment added", toLegacyComment(response)));
    }

    private CommunityPostResponse toCommunityPost(PrayerPostResponse post) {
        return new CommunityPostResponse(
                post.id(),
                post.title(),
                post.content(),
                null,
                post.postType() == PrayerPostType.OFFICIAL,
                null,
                post.publishedAt(),
                post.stats().amenCount(),
                post.stats().commentCount(),
                post.viewerState().hasAmened(),
                post.createdAt()
        );
    }

    private PostCommentResponse toLegacyComment(PrayerCommentResponse comment) {
        return new PostCommentResponse(
                comment.id(),
                comment.postId(),
                comment.author().accountHolderId(),
                comment.content(),
                null,
                List.of(),
                comment.createdAt()
        );
    }

    private HttpHeaders deprecationHeaders(String replacementRoute) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Deprecated-Route", "true");
        headers.add("X-Replacement-Route", replacementRoute);
        return headers;
    }
}
