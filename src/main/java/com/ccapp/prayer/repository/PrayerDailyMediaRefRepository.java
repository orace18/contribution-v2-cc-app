package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerDailyMediaRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrayerDailyMediaRefRepository extends JpaRepository<PrayerDailyMediaRef, UUID> {

    List<PrayerDailyMediaRef> findByDailyPrayerIdOrderBySortOrderAsc(UUID dailyPrayerId);
}
