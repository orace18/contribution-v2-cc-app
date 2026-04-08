package com.ccapp.prayer.controller.admin;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.dto.AdminCreatePrayerPostRequest;
import com.ccapp.prayer.dto.AdminPrayerPostResponse;
import com.ccapp.prayer.dto.AdminUpdatePrayerPostRequest;
import com.ccapp.prayer.service.PrayerAdminPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/prayers/posts")
@Tag(name = "Prayer Admin Posts", description = "Admin management for official Business Prayer posts")
@SecurityRequirement(name = "bearerAuth")
public class AdminPrayerPostController {

    private final PrayerAdminPostService prayerAdminPostService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    @Operation(summary = "List official prayer posts")
    public ResponseEntity<CommonResponse<PageResponse<AdminPrayerPostResponse>>> listPosts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) PrayerPostLifecycleStatus status,
            @RequestParam(required = false) PrayerOfficialVisibilityScope visibility) {
        PageResponse<AdminPrayerPostResponse> response = prayerAdminPostService.listPosts(page, size, status, visibility);
        return ResponseEntity.ok(CommonResponse.success("Official prayer posts", response));
    }

    @PostMapping
    @Operation(summary = "Create an official prayer post draft")
    public ResponseEntity<CommonResponse<AdminPrayerPostResponse>> createPost(
            @Valid @RequestBody AdminCreatePrayerPostRequest request) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminPrayerPostResponse response = prayerAdminPostService.createPost(adminUserId, request);
        adminAuditService.logAction(adminUserId, "PRAYER_POST_CREATED", "PRAYER_POST", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Official prayer post created", response));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get an official prayer post")
    public ResponseEntity<CommonResponse<AdminPrayerPostResponse>> getPost(@PathVariable UUID postId) {
        AdminPrayerPostResponse response = prayerAdminPostService.getPost(postId);
        return ResponseEntity.ok(CommonResponse.success("Official prayer post", response));
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "Update an official prayer post draft")
    public ResponseEntity<CommonResponse<AdminPrayerPostResponse>> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody AdminUpdatePrayerPostRequest request) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminPrayerPostResponse response = prayerAdminPostService.updatePost(adminUserId, postId, request);
        adminAuditService.logAction(adminUserId, "PRAYER_POST_UPDATED", "PRAYER_POST", postId.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Official prayer post updated", response));
    }

    @PostMapping("/{postId}/publish")
    @Operation(summary = "Publish an official prayer post")
    public ResponseEntity<CommonResponse<AdminPrayerPostResponse>> publishPost(@PathVariable UUID postId) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminPrayerPostResponse response = prayerAdminPostService.publishPost(adminUserId, postId);
        adminAuditService.logAction(adminUserId, "PRAYER_POST_PUBLISHED", "PRAYER_POST", postId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Official prayer post published", response));
    }

    @PostMapping("/{postId}/archive")
    @Operation(summary = "Archive an official prayer post")
    public ResponseEntity<CommonResponse<AdminPrayerPostResponse>> archivePost(@PathVariable UUID postId) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminPrayerPostResponse response = prayerAdminPostService.archivePost(adminUserId, postId);
        adminAuditService.logAction(adminUserId, "PRAYER_POST_ARCHIVED", "PRAYER_POST", postId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Official prayer post archived", response));
    }
}
