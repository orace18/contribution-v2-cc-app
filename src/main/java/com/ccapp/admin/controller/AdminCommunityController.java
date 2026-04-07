package com.ccapp.admin.controller;

import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.community.dto.CommunityPostResponse;
import com.ccapp.community.dto.CreateCommunityPostRequest;
import com.ccapp.community.dto.FeatureCommunityPostRequest;
import com.ccapp.community.service.CommunityAdminService;
import com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.dto.AdminCreatePrayerPostRequest;
import com.ccapp.prayer.dto.AdminPrayerPostResponse;
import com.ccapp.prayer.service.PrayerAdminPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 * @deprecated Replaced by {@code com.ccapp.prayer.controller.admin.AdminPrayerPostController}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
@RestController
@RequestMapping("/api/admin/community/posts")
@RequiredArgsConstructor
@Tag(name = "Admin Community", description = "Admin management of community posts")
@SecurityRequirement(name = "bearerAuth")
public class AdminCommunityController {

    private final CommunityAdminService communityAdminService;
    private final PrayerAdminPostService prayerAdminPostService;

    @GetMapping
    @Operation(summary = "List all community posts")
    public ResponseEntity<CommonResponse<List<CommunityPostResponse>>> list() {
        List<CommunityPostResponse> response = prayerAdminPostService.listPosts(0, 100, null, null).items().stream()
                .map(this::toCommunityPost)
                .toList();
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/admin/prayers/posts"))
                .body(CommonResponse.success("Community posts", response));
    }

    @PostMapping
    @Operation(summary = "Create a community post")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CommunityPostResponse>> create(@Valid @RequestBody CreateCommunityPostRequest request) {
        if (request.mediaUrl() != null && !request.mediaUrl().isBlank()) {
            throw new BusinessException("LEGACY_COMMUNITY_MEDIA_URL_NOT_SUPPORTED");
        }
        AdminPrayerPostResponse created = prayerAdminPostService.createPost(
                AdminSecurityUtils.currentAdminUserId(),
                new AdminCreatePrayerPostRequest(
                        request.title(),
                        request.content(),
                        mapLegacyVisibility(request.visibility()),
                        resolveLegacyTargetCountryCode(request.visibility()),
                        null,
                        List.of(),
                        null
                )
        );
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/admin/prayers/posts"))
                .body(CommonResponse.success("Community post created", toCommunityPost(created)));
    }

    @PostMapping("/{id}/feature")
    @Operation(summary = "Feature a community post")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Success",
                    content = @Content(
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Community post featured\",\"data\":{\"id\":\"c5dd8e13-2806-4064-8920-39cd4778ad95\",\"title\":\"Top story\",\"content\":\"...\",\"mediaUrl\":null,\"featured\":true,\"featuredPriority\":1,\"featuredAt\":\"2026-03-10T12:00:00Z\",\"createdAt\":\"2026-03-05T08:00:00Z\"},\"timestamp\":\"2026-03-10T12:00:00Z\"}")
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CommunityPostResponse>> feature(@PathVariable UUID id,
                                                                         @Valid @RequestBody FeatureCommunityPostRequest request) {
        AdminPrayerPostResponse published = prayerAdminPostService.publishPost(AdminSecurityUtils.currentAdminUserId(), id);
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/admin/prayers/posts/" + id + "/publish"))
                .body(CommonResponse.success("Community post featured", toCommunityPost(published)));
    }

    @PostMapping("/{id}/unfeature")
    @Operation(summary = "Unfeature a community post")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Success",
                    content = @Content(
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Community post unfeatured\",\"data\":{\"id\":\"c5dd8e13-2806-4064-8920-39cd4778ad95\",\"title\":\"Top story\",\"content\":\"...\",\"mediaUrl\":null,\"featured\":false,\"featuredPriority\":null,\"featuredAt\":null,\"createdAt\":\"2026-03-05T08:00:00Z\"},\"timestamp\":\"2026-03-10T12:10:00Z\"}")
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CommunityPostResponse>> unfeature(@PathVariable UUID id) {
        AdminPrayerPostResponse archived = prayerAdminPostService.archivePost(AdminSecurityUtils.currentAdminUserId(), id);
        return ResponseEntity.ok()
                .headers(deprecationHeaders("/api/admin/prayers/posts/" + id + "/archive"))
                .body(CommonResponse.success("Community post unfeatured", toCommunityPost(archived)));
    }

    private CommunityPostResponse toCommunityPost(AdminPrayerPostResponse post) {
        return new CommunityPostResponse(
                post.id(),
                post.title(),
                post.content(),
                post.mediaRefs().stream()
                        .map(com.ccapp.prayer.dto.PrayerMediaRefResponse::downloadUrl)
                        .filter(url -> url != null && !url.isBlank())
                        .findFirst()
                        .orElse(null),
                post.lifecycleStatus() == PrayerPostLifecycleStatus.PUBLISHED,
                null,
                post.publishedAt(),
                0,
                0,
                false,
                post.createdAt()
        );
    }

    private PrayerOfficialVisibilityScope mapLegacyVisibility(String visibility) {
        if (visibility == null || visibility.isBlank() || "PUBLIC".equalsIgnoreCase(visibility) || "GLOBAL".equalsIgnoreCase(visibility)) {
            return PrayerOfficialVisibilityScope.GLOBAL;
        }
        if ("COMMUNITY".equalsIgnoreCase(visibility)) {
            return PrayerOfficialVisibilityScope.COMMUNITY;
        }
        if ("CHURCH".equalsIgnoreCase(visibility)) {
            return PrayerOfficialVisibilityScope.CHURCH;
        }
        return PrayerOfficialVisibilityScope.GLOBAL;
    }

    private String resolveLegacyTargetCountryCode(String visibility) {
        if ("COMMUNITY".equalsIgnoreCase(visibility)) {
            return AdminSecurityUtils.currentPrincipal().countryCode();
        }
        return null;
    }

    private HttpHeaders deprecationHeaders(String replacementRoute) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Deprecated-Route", "true");
        headers.add("X-Replacement-Route", replacementRoute);
        return headers;
    }
}
