package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerUserDailyMission;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrayerUserDailyMissionRepository extends JpaRepository<PrayerUserDailyMission, UUID> {

    List<PrayerUserDailyMission> findByAccountHolderIdAndMissionDateOrderByMissionCodeAsc(UUID accountHolderId, LocalDate missionDate);

    Optional<PrayerUserDailyMission> findByAccountHolderIdAndMissionDateAndMissionCode(
            UUID accountHolderId,
            LocalDate missionDate,
            PrayerDailyMissionCode missionCode
    );
}
