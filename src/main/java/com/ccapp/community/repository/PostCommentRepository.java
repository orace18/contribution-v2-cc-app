package com.ccapp.community.repository;

import com.ccapp.community.domain.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    List<PostComment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(UUID postId);

    List<PostComment> findByParentCommentIdOrderByCreatedAtAsc(UUID parentCommentId);

    long countByPostId(UUID postId);
}
