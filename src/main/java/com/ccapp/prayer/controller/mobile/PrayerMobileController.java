package com.ccapp.prayer.controller.mobile;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.dto.CompleteDailyPrayerRequest;
import com.ccapp.prayer.dto.CreatePrayerCommentRequest;
import com.ccapp.prayer.dto.CreatePrayerRequest;
import com.ccapp.prayer.dto.DailyPrayerCompletionResponse;
import com.ccapp.prayer.dto.DailyPrayerResponse;
import com.ccapp.prayer.dto.PrayerCommentResponse;
import com.ccapp.prayer.dto.PrayerDashboardResponse;
import com.ccapp.prayer.dto.PrayerInteractionResponse;
import com.ccapp.prayer.dto.PrayerPostResponse;
import com.ccapp.prayer.dto.UpdatePrayerRequest;
import com.ccapp.prayer.service.PrayerMobileService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/mobile/prayers")
@Tag(name = "Prayer Mobile", description = "Business Prayer mobile endpoints")
@SecurityRequirement(name = "bearerAuth")
public class PrayerMobileController {

    private final PrayerMobileService prayerMobileService;

    @GetMapping("/feed")
    @Operation(summary = "List visible prayer feed items")
    public ResponseEntity<CommonResponse<PageResponse<PrayerPostResponse>>> feed(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "true") boolean includeMinePrivate) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PageResponse<PrayerPostResponse> response = prayerMobileService.getPrayerFeed(
                principal.userId(),
                accountHolderId,
                page,
                size,
                includeMinePrivate
        );
        return ResponseEntity.ok(CommonResponse.success("Prayer feed", response));
    }

    @PostMapping("/requests")
    @Operation(summary = "Publish a prayer request")
    public ResponseEntity<CommonResponse<PrayerPostResponse>> createPrayerRequest(
            @Valid @RequestBody CreatePrayerRequest request) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerPostResponse response = prayerMobileService.createPrayerRequest(principal.userId(), accountHolderId, request);
        return ResponseEntity.ok(CommonResponse.success("Prayer request created", response));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "Get a visible prayer post detail")
    public ResponseEntity<CommonResponse<PrayerPostResponse>> getPrayerPost(@PathVariable UUID postId) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerPostResponse response = prayerMobileService.getPrayerPost(principal.userId(), accountHolderId, postId);
        return ResponseEntity.ok(CommonResponse.success("Prayer post", response));
    }

    @PatchMapping("/posts/{postId}")
    @Operation(summary = "Update the current user's prayer request")
    public ResponseEntity<CommonResponse<PrayerPostResponse>> updatePrayerPost(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePrayerRequest request) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerPostResponse response = prayerMobileService.updatePrayerRequest(principal.userId(), accountHolderId, postId, request);
        return ResponseEntity.ok(CommonResponse.success("Prayer post updated", response));
    }

    @PostMapping("/posts/{postId}/archive")
    @Operation(summary = "Archive the current user's prayer request")
    public ResponseEntity<CommonResponse<PrayerPostResponse>> archivePrayerPost(@PathVariable UUID postId) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerPostResponse response = prayerMobileService.archivePrayerRequest(principal.userId(), accountHolderId, postId);
        return ResponseEntity.ok(CommonResponse.success("Prayer post archived", response));
    }

    @PutMapping("/posts/{postId}/amen")
    @Operation(summary = "Record an Amen reaction on a visible post")
    public ResponseEntity<CommonResponse<PrayerInteractionResponse>> recordAmen(@PathVariable UUID postId) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerInteractionResponse response = prayerMobileService.recordAmen(principal.userId(), accountHolderId, postId);
        return ResponseEntity.ok(CommonResponse.success("Prayer amen recorded", response));
    }

    @PutMapping("/posts/{postId}/support")
    @Operation(summary = "Record an \"I prayed for this person\" reaction on a visible request post")
    public ResponseEntity<CommonResponse<PrayerInteractionResponse>> recordSupport(@PathVariable UUID postId) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerInteractionResponse response = prayerMobileService.recordSupport(principal.userId(), accountHolderId, postId);
        return ResponseEntity.ok(CommonResponse.success("Prayer support recorded", response));
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "List comments for a visible prayer post")
    public ResponseEntity<CommonResponse<PageResponse<PrayerCommentResponse>>> listComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PageResponse<PrayerCommentResponse> response = prayerMobileService.listPrayerComments(
                principal.userId(),
                accountHolderId,
                postId,
                page,
                size
        );
        return ResponseEntity.ok(CommonResponse.success("Prayer comments", response));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "Add a comment to a visible prayer post")
    public ResponseEntity<CommonResponse<PrayerCommentResponse>> createComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CreatePrayerCommentRequest request) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerCommentResponse response = prayerMobileService.createPrayerComment(
                principal.userId(),
                accountHolderId,
                postId,
                request
        );
        return ResponseEntity.ok(CommonResponse.success("Prayer comment created", response));
    }

    @GetMapping("/daily/current")
    @Operation(summary = "Get the current published daily prayer")
    public ResponseEntity<CommonResponse<DailyPrayerResponse>> getCurrentDailyPrayer(
            @RequestParam(required = false) String timezone) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        DailyPrayerResponse response = prayerMobileService.getCurrentDailyPrayer(principal.userId(), accountHolderId, timezone);
        return ResponseEntity.ok(CommonResponse.success("Current daily prayer", response));
    }

    @PostMapping("/daily/current/complete")
    @Operation(summary = "Record completion of today's daily prayer")
    public ResponseEntity<CommonResponse<DailyPrayerCompletionResponse>> completeCurrentDailyPrayer(
            @RequestParam(required = false) String timezone,
            @Valid @RequestBody(required = false) CompleteDailyPrayerRequest request) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerDailyPrayerResponseType responseType = request == null
                ? PrayerDailyPrayerResponseType.PRAYED
                : request.responseType();
        DailyPrayerCompletionResponse response = prayerMobileService.completeCurrentDailyPrayer(
                principal.userId(),
                accountHolderId,
                timezone,
                responseType
        );
        return ResponseEntity.ok(CommonResponse.success("Daily prayer completed", response));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get current prayer streak, missions, and engagement stats")
    public ResponseEntity<CommonResponse<PrayerDashboardResponse>> getDashboard(
            @RequestParam(required = false) String timezone) {
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID accountHolderId = SecurityUtils.currentAccountHolderId();
        PrayerDashboardResponse response = prayerMobileService.getPrayerDashboard(principal.userId(), accountHolderId, timezone);
        return ResponseEntity.ok(CommonResponse.success("Prayer dashboard", response));
    }
}
