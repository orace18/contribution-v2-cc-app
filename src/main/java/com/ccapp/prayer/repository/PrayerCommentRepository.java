package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrayerCommentRepository extends JpaRepository<PrayerComment, UUID> {

    List<PrayerComment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    List<PrayerComment> findByPostIdOrderByCreatedAtAscIdAsc(UUID postId);

    Optional<PrayerComment> findByAccountHolderIdAndClientRequestId(UUID accountHolderId, UUID clientRequestId);

    long countByAccountHolderId(UUID accountHolderId);

    @Query("""
            select count(pc)
            from PrayerComment pc
            join PrayerPost p on p.id = pc.postId
            where p.authorAccountHolderId = :accountHolderId
            """)
    long countReceivedByAccountHolderId(@Param("accountHolderId") UUID accountHolderId);

    long countByPostId(UUID postId);
}
