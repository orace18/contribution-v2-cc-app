package com.ccapp.community.service;

import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.community.domain.entity.PostComment;
import com.ccapp.community.domain.entity.PostLike;
import com.ccapp.community.dto.PostCommentRequest;
import com.ccapp.community.dto.PostCommentResponse;
import com.ccapp.community.repository.CommunityPostRepository;
import com.ccapp.community.repository.PostCommentRepository;
import com.ccapp.community.repository.PostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * @deprecated Replaced by the Business Prayer interaction model in {@code com.ccapp.prayer}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
@Service
@RequiredArgsConstructor
public class CommunityEngagementService {

    private final CommunityPostRepository communityPostRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;

    @Transactional
    public void likePost(UUID postId, UUID accountHolderId) {
        ensurePostExists(postId);
        if (postLikeRepository.existsByPostIdAndAccountHolderId(postId, accountHolderId)) {
            throw new ConflictException("Post already liked");
        }
        postLikeRepository.save(PostLike.builder()
                .postId(postId)
                .accountHolderId(accountHolderId)
                .build());
    }

    @Transactional
    public void unlikePost(UUID postId, UUID accountHolderId) {
        ensurePostExists(postId);
        postLikeRepository.deleteByPostIdAndAccountHolderId(postId, accountHolderId);
    }

    @Transactional(readOnly = true)
    public List<PostCommentResponse> listComments(UUID postId) {
        ensurePostExists(postId);
        List<PostComment> roots = postCommentRepository.findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(postId);
        return roots.stream().map(this::toResponseWithReplies).toList();
    }

    @Transactional
    public PostCommentResponse addComment(UUID postId, UUID accountHolderId, PostCommentRequest request) {
        ensurePostExists(postId);
        PostComment comment = postCommentRepository.save(PostComment.builder()
                .postId(postId)
                .accountHolderId(accountHolderId)
                .content(request.content().trim())
                .parentCommentId(request.parentCommentId())
                .build());
        return toResponseWithReplies(comment);
    }

    private PostCommentResponse toResponseWithReplies(PostComment comment) {
        List<PostCommentResponse> replies = postCommentRepository
                .findByParentCommentIdOrderByCreatedAtAsc(comment.getId())
                .stream()
                .map(this::toResponseWithReplies)
                .toList();
        return new PostCommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getAccountHolderId(),
                comment.getContent(),
                comment.getParentCommentId(),
                replies,
                comment.getCreatedAt()
        );
    }

    private void ensurePostExists(UUID postId) {
        if (!communityPostRepository.existsById(postId)) {
            throw new NotFoundException("Community post not found");
        }
    }
}
