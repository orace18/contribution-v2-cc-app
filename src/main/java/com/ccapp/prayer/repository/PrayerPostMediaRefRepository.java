package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerPostMediaRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrayerPostMediaRefRepository extends JpaRepository<PrayerPostMediaRef, UUID> {

    List<PrayerPostMediaRef> findByPostIdOrderBySortOrderAsc(UUID postId);
}
