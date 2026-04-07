package com.ccapp.admin.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.community.dto.CommunityPostResponse;
import com.ccapp.community.dto.CreateCommunityPostRequest;
import com.ccapp.community.dto.FeatureCommunityPostRequest;
import com.ccapp.community.service.CommunityAdminService;
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

    @GetMapping
    @Operation(summary = "List all community posts")
    public ResponseEntity<CommonResponse<List<CommunityPostResponse>>> list() {
        return ResponseEntity.ok(CommonResponse.success("Community posts", communityAdminService.listPosts()));
    }

    @PostMapping
    @Operation(summary = "Create a community post")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CommunityPostResponse>> create(@Valid @RequestBody CreateCommunityPostRequest request) {
        CommunityPostResponse response = communityAdminService.create(request);
        return ResponseEntity.ok(CommonResponse.success("Community post created", response));
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
        CommunityPostResponse response = communityAdminService.feature(id, request.priority());
        return ResponseEntity.ok(CommonResponse.success("Community post featured", response));
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
        CommunityPostResponse response = communityAdminService.unfeature(id);
        return ResponseEntity.ok(CommonResponse.success("Community post unfeatured", response));
    }
}
