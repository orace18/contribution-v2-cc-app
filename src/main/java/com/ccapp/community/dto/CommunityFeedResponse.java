package com.ccapp.community.dto;

import java.util.List;

public record CommunityFeedResponse(
        List<CommunityPostResponse> featuredPosts,
        List<CommunityPostResponse> posts
) {
}
