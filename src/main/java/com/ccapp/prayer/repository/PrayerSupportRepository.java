package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerSupport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrayerSupportRepository extends JpaRepository<PrayerSupport, UUID> {

    boolean existsByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    Optional<PrayerSupport> findByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    long countByPostId(UUID postId);
}
