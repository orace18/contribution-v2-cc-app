package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrayerDailyContentRepository extends JpaRepository<PrayerDailyContent, UUID> {

    Optional<PrayerDailyContent> findByPrayerDate(LocalDate prayerDate);

    Optional<PrayerDailyContent> findByPrayerDateAndStatus(LocalDate prayerDate, PrayerDailyPrayerStatus status);

    List<PrayerDailyContent> findByStatusOrderByPrayerDateDesc(PrayerDailyPrayerStatus status);
}
