package com.ccapp.community.repository;

import com.ccapp.community.domain.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    Optional<PostLike> findByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    long countByPostId(UUID postId);

    void deleteByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);
}
