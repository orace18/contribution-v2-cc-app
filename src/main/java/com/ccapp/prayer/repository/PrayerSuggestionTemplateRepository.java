package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerSuggestionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrayerSuggestionTemplateRepository extends JpaRepository<PrayerSuggestionTemplate, UUID> {

    List<PrayerSuggestionTemplate> findByActiveTrueOrderBySortOrderAscIdAsc();
}
