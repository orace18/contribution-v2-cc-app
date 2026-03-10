package com.ccapp.community.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.community.dto.CommunityFeedResponse;
import com.ccapp.community.service.CommunityFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/community")
@RequiredArgsConstructor
@Tag(name = "Mobile Community", description = "Community feed for mobile home screen")
@SecurityRequirement(name = "bearerAuth")
public class MobileCommunityController {

    private final CommunityFeedService communityFeedService;

    @GetMapping("/feed")
    @Operation(summary = "Get mobile community feed")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Success",
                    content = @Content(
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Community feed\",\"data\":{\"featuredPosts\":[{\"id\":\"c5dd8e13-2806-4064-8920-39cd4778ad95\",\"title\":\"Top story\",\"content\":\"...\",\"mediaUrl\":null,\"featured\":true,\"featuredPriority\":1,\"featuredAt\":\"2026-03-10T12:00:00Z\",\"createdAt\":\"2026-03-05T08:00:00Z\"}],\"posts\":[{\"id\":\"f0e94655-e6d6-4ff8-9f34-d66226bcf4ce\",\"title\":\"Regular post\",\"content\":\"...\",\"mediaUrl\":null,\"featured\":false,\"featuredPriority\":null,\"featuredAt\":null,\"createdAt\":\"2026-03-10T11:00:00Z\"}]},\"timestamp\":\"2026-03-10T12:30:00Z\"}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CommunityFeedResponse>> feed() {
        return ResponseEntity.ok(CommonResponse.success("Community feed", communityFeedService.getMobileFeed()));
    }
}
