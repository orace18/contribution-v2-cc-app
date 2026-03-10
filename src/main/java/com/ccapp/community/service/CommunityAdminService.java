package com.ccapp.community.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.community.domain.entity.CommunityPost;
import com.ccapp.community.dto.CommunityPostResponse;
import com.ccapp.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityAdminService {

    private static final int MAX_FEATURED_POSTS = 5;

    private final CommunityPostRepository communityPostRepository;

    @Transactional
    public CommunityPostResponse feature(UUID postId, int priority) {
        CommunityPost post = communityPostRepository.findByIdAndActiveTrue(postId)
                .orElseThrow(() -> new NotFoundException("Community post not found"));

        long featuredCount = communityPostRepository.countByActiveTrueAndFeaturedTrue();
        if (!post.isFeatured() && featuredCount >= MAX_FEATURED_POSTS) {
            throw new BusinessException("MAX_FEATURED_POSTS_REACHED");
        }

        post.setFeatured(true);
        post.setFeaturedPriority(priority);
        post.setFeaturedAt(OffsetDateTime.now());
        CommunityPost saved = communityPostRepository.save(post);
        return toResponse(saved);
    }

    @Transactional
    public CommunityPostResponse unfeature(UUID postId) {
        CommunityPost post = communityPostRepository.findByIdAndActiveTrue(postId)
                .orElseThrow(() -> new NotFoundException("Community post not found"));

        post.setFeatured(false);
        post.setFeaturedPriority(null);
        post.setFeaturedAt(null);
        CommunityPost saved = communityPostRepository.save(post);
        return toResponse(saved);
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
