package com.ccapp.community.dto;

import java.util.List;

/**
 * @deprecated Replaced by prayer feed responses under {@code com.ccapp.prayer.dto}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public record CommunityFeedResponse(
        List<CommunityPostResponse> featuredPosts,
        List<CommunityPostResponse> posts
) {
}
