package com.ccapp.community.repository;

import com.ccapp.community.domain.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * @deprecated Legacy repository for the old community feed tables. Replaced by {@code com.ccapp.prayer.repository.PrayerCommentRepository}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    List<PostComment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(UUID postId);

    List<PostComment> findByParentCommentIdOrderByCreatedAtAsc(UUID parentCommentId);

    long countByPostId(UUID postId);
}
