package com.ccapp.community.service;

import com.ccapp.community.domain.entity.CommunityPost;
import com.ccapp.community.dto.CommunityFeedResponse;
import com.ccapp.community.dto.CommunityPostResponse;
import com.ccapp.community.repository.CommunityPostRepository;
import com.ccapp.community.repository.PostCommentRepository;
import com.ccapp.community.repository.PostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityFeedService {

    private final CommunityPostRepository communityPostRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;

    @Transactional(readOnly = true)
    public CommunityFeedResponse getMobileFeed(UUID currentAccountHolderId) {
        List<CommunityPostResponse> featuredPosts = communityPostRepository.findFeaturedForFeed().stream()
                .map(p -> toResponse(p, currentAccountHolderId))
                .toList();
        List<CommunityPostResponse> posts = communityPostRepository.findByActiveTrueAndFeaturedFalseOrderByCreatedAtDesc().stream()
                .map(p -> toResponse(p, currentAccountHolderId))
                .toList();
        return new CommunityFeedResponse(featuredPosts, posts);
    }

    CommunityPostResponse toResponse(CommunityPost post, UUID currentAccountHolderId) {
        long likesCount = postLikeRepository.countByPostId(post.getId());
        long commentsCount = postCommentRepository.countByPostId(post.getId());
        boolean likedByCurrentUser = currentAccountHolderId != null
                && postLikeRepository.existsByPostIdAndAccountHolderId(post.getId(), currentAccountHolderId);
        return new CommunityPostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMediaUrl(),
                post.isFeatured(),
                post.getFeaturedPriority(),
                post.getFeaturedAt(),
                likesCount,
                commentsCount,
                likedByCurrentUser,
                post.getCreatedAt()
        );
    }
}
