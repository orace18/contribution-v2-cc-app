package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PrayerPostRepository extends JpaRepository<PrayerPost, UUID> {

    Optional<PrayerPost> findByAuthorAccountHolderIdAndClientRequestId(UUID authorAccountHolderId, UUID clientRequestId);

    long countByAuthorAccountHolderIdAndPostType(UUID authorAccountHolderId, PrayerPostType postType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrayerPost p where p.id = :id")
    Optional<PrayerPost> findByIdForUpdate(@Param("id") UUID id);
}
