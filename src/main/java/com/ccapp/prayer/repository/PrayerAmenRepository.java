package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerAmen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrayerAmenRepository extends JpaRepository<PrayerAmen, UUID> {

    boolean existsByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    Optional<PrayerAmen> findByPostIdAndAccountHolderId(UUID postId, UUID accountHolderId);

    long countByPostId(UUID postId);
}
