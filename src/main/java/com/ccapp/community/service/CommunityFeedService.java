package com.ccapp.community.service;

import com.ccapp.community.domain.entity.CommunityPost;
import com.ccapp.community.dto.CommunityFeedResponse;
import com.ccapp.community.dto.CommunityPostResponse;
import com.ccapp.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityFeedService {

    private final CommunityPostRepository communityPostRepository;

    @Transactional(readOnly = true)
    public CommunityFeedResponse getMobileFeed() {
        List<CommunityPostResponse> featuredPosts = communityPostRepository.findFeaturedForFeed().stream()
                .map(this::toResponse)
                .toList();
        List<CommunityPostResponse> posts = communityPostRepository.findByActiveTrueAndFeaturedFalseOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
        return new CommunityFeedResponse(featuredPosts, posts);
    }

    private CommunityPostResponse toResponse(CommunityPost post) {
        return new CommunityPostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMediaUrl(),
                post.isFeatured(),
                post.getFeaturedPriority(),
                post.getFeaturedAt(),
                post.getCreatedAt()
        );
    }
}
