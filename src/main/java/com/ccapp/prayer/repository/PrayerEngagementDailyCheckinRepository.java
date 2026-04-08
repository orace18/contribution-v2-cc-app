package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerEngagementDailyCheckin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PrayerEngagementDailyCheckinRepository extends JpaRepository<PrayerEngagementDailyCheckin, UUID> {

    Optional<PrayerEngagementDailyCheckin> findByAccountHolderIdAndLocalDate(UUID accountHolderId, LocalDate localDate);
}
