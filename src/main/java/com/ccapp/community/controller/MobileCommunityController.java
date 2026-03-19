package com.ccapp.community.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.community.dto.CommunityFeedResponse;
import com.ccapp.community.dto.PostCommentRequest;
import com.ccapp.community.dto.PostCommentResponse;
import com.ccapp.community.service.CommunityEngagementService;
import com.ccapp.community.service.CommunityFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/community")
@RequiredArgsConstructor
@Tag(name = "Mobile Community", description = "Community feed for mobile home screen")
@SecurityRequirement(name = "bearerAuth")
public class MobileCommunityController {

    private final CommunityFeedService communityFeedService;
    private final CommunityEngagementService communityEngagementService;

    @GetMapping("/feed")
    @Operation(summary = "Get mobile community feed")
    public ResponseEntity<CommonResponse<CommunityFeedResponse>> feed() {
        UUID currentUserId = SecurityUtils.currentAccountHolderId();
        return ResponseEntity.ok(CommonResponse.success("Community feed", communityFeedService.getMobileFeed(currentUserId)));
    }

    @PostMapping("/posts/{postId}/like")
    @Operation(summary = "Like a community post")
    public ResponseEntity<CommonResponse<Void>> likePost(@PathVariable UUID postId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        communityEngagementService.likePost(postId, accountHolderId);
        return ResponseEntity.ok(CommonResponse.success("Post liked", null));
    }

    @DeleteMapping("/posts/{postId}/like")
    @Operation(summary = "Unlike a community post")
    public ResponseEntity<CommonResponse<Void>> unlikePost(@PathVariable UUID postId) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        communityEngagementService.unlikePost(postId, accountHolderId);
        return ResponseEntity.ok(CommonResponse.success("Post unliked", null));
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "List comments for a community post")
    public ResponseEntity<CommonResponse<List<PostCommentResponse>>> listComments(@PathVariable UUID postId) {
        return ResponseEntity.ok(CommonResponse.success("Post comments", communityEngagementService.listComments(postId)));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "Add a comment to a community post")
    public ResponseEntity<CommonResponse<PostCommentResponse>> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody PostCommentRequest request) {
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PostCommentResponse response = communityEngagementService.addComment(postId, accountHolderId, request);
        return ResponseEntity.ok(CommonResponse.success("Comment added", response));
    }
}
