package com.ccapp.community.repository;

import com.ccapp.community.domain.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * @deprecated Legacy repository for the old community feed tables. Replaced by {@code com.ccapp.prayer.repository.PrayerAmenRepository}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    Optional<PostLike> findByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    long countByPostId(UUID postId);

    void deleteByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);
}
