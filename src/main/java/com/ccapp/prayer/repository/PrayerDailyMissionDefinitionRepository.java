package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerDailyMissionDefinition;
import com.ccapp.prayer.domain.enums.PrayerDailyMissionCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrayerDailyMissionDefinitionRepository extends JpaRepository<PrayerDailyMissionDefinition, UUID> {

    List<PrayerDailyMissionDefinition> findByActiveTrueOrderBySortOrderAsc();

    Optional<PrayerDailyMissionDefinition> findByMissionCode(PrayerDailyMissionCode missionCode);
}
