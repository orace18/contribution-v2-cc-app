package com.ccapp.community.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.community.domain.entity.CommunityPost;
import com.ccapp.community.dto.CommunityPostResponse;
import com.ccapp.community.dto.CreateCommunityPostRequest;
import com.ccapp.community.event.PostCreatedEvent;
import com.ccapp.community.repository.CommunityPostRepository;
import com.ccapp.community.repository.PostCommentRepository;
import com.ccapp.community.repository.PostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @deprecated Replaced by {@code com.ccapp.prayer.service.PrayerAdminPostService}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
@Service
@RequiredArgsConstructor
public class CommunityAdminService {

    private static final int MAX_FEATURED_POSTS = 5;

    private final CommunityPostRepository communityPostRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional(readOnly = true)
    public List<CommunityPostResponse> listPosts() {
        return communityPostRepository.findAll().stream()
                .filter(CommunityPost::isActive)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CommunityPostResponse create(CreateCommunityPostRequest request) {
        CommunityPost saved = communityPostRepository.save(CommunityPost.builder()
                .title(request.title().trim())
                .content(request.content().trim())
                .mediaUrl(request.mediaUrl())
                .authorAccountHolderId(request.authorAccountHolderId())
                .communityId(request.communityId())
                .visibility(normalizeVisibility(request.visibility()))
                .active(true)
                .featured(false)
                .featuredPriority(null)
                .featuredAt(null)
                .build());

        domainEventPublisher.publish(PostCreatedEvent.create(
                saved.getId(),
                saved.getAuthorAccountHolderId(),
                saved.getCommunityId(),
                saved.getVisibility()
        ));
        return toResponse(saved);
    }

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
        long likesCount = postLikeRepository.countByPostId(post.getId());
        long commentsCount = postCommentRepository.countByPostId(post.getId());
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
                false,
                post.getCreatedAt()
        );
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return "PUBLIC";
        }
        return visibility.trim().toUpperCase();
    }
}
