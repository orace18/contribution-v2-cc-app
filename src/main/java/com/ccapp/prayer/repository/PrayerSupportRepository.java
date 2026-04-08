package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerSupport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PrayerSupportRepository extends JpaRepository<PrayerSupport, UUID> {

    boolean existsByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    Optional<PrayerSupport> findByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    long countByAccountHolderId(UUID accountHolderId);

    @Query("""
            select count(ps)
            from PrayerSupport ps
            join PrayerPost p on p.id = ps.postId
            where p.authorAccountHolderId = :accountHolderId
            """)
    long countReceivedByAccountHolderId(@Param("accountHolderId") UUID accountHolderId);

    long countByPostId(UUID postId);
}
