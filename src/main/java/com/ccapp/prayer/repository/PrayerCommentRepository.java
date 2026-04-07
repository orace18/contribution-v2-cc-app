package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrayerCommentRepository extends JpaRepository<PrayerComment, UUID> {

    List<PrayerComment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    List<PrayerComment> findByPostIdOrderByCreatedAtAscIdAsc(UUID postId);

    Optional<PrayerComment> findByAccountHolderIdAndClientRequestId(UUID accountHolderId, UUID clientRequestId);

    long countByPostId(UUID postId);
}
