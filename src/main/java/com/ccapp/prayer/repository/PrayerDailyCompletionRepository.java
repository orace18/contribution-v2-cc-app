package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerDailyCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrayerDailyCompletionRepository extends JpaRepository<PrayerDailyCompletion, UUID> {

    Optional<PrayerDailyCompletion> findByAccountHolderIdAndPrayerDate(UUID accountHolderId, LocalDate prayerDate);

    List<PrayerDailyCompletion> findByAccountHolderIdOrderByPrayerDateDesc(UUID accountHolderId);
}
