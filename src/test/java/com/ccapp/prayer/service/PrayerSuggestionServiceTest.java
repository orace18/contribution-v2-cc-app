package com.ccapp.prayer.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.prayer.domain.entity.PrayerDailyCompletion;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerSuggestionTemplate;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerResponseType;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.domain.enums.PrayerHomeSuggestionSourceType;
import com.ccapp.prayer.domain.enums.PrayerSuggestionAudioStatus;
import com.ccapp.prayer.domain.enums.PrayerSuggestionCategory;
import com.ccapp.prayer.dto.PrayerHomeSuggestionResponse;
import com.ccapp.prayer.repository.PrayerDailyCompletionRepository;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import com.ccapp.prayer.repository.PrayerSuggestionTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        PrayerSuggestionService.class,
        PrayerTimezoneResolver.class
})
class PrayerSuggestionServiceTest {

    private static final String TEST_TIMEZONE = "Africa/Porto-Novo";

    @Autowired
    private PrayerSuggestionService prayerSuggestionService;
    @Autowired
    private PrayerDailyContentRepository prayerDailyContentRepository;
    @Autowired
    private PrayerDailyCompletionRepository prayerDailyCompletionRepository;
    @Autowired
    private PrayerSuggestionTemplateRepository prayerSuggestionTemplateRepository;

    @Test
    void getHomeSuggestion_shouldPrioritizeCurrentDailyPrayerWhenItIsNotCompleted() {
        AccountHolder accountHolder = AccountHolder.builder()
                .id(UUID.randomUUID())
                .serviceCountryCode("BJ")
                .build();
        LocalDate today = LocalDate.now(ZoneId.of(TEST_TIMEZONE));
        PrayerDailyContent dailyPrayer = prayerDailyContentRepository.save(PrayerDailyContent.builder()
                .prayerDate(today)
                .title("Priere du jour")
                .content("Fortifie mon activite, Seigneur.")
                .status(PrayerDailyPrayerStatus.PUBLISHED)
                .createdByAdminUserId(UUID.randomUUID())
                .publishedAt(OffsetDateTime.now())
                .build());
        seedTemplate("PROVISION_CLIENTS", PrayerSuggestionCategory.PROVISION, 10);

        PrayerHomeSuggestionResponse suggestion = prayerSuggestionService.getHomeSuggestion(
                accountHolder,
                false,
                false,
                true
        );

        assertEquals(PrayerHomeSuggestionSourceType.DAILY_PRAYER, suggestion.sourceType());
        assertEquals(dailyPrayer.getId(), suggestion.referenceId());
        assertEquals("Prier maintenant", suggestion.deepLink().label());
    }

    @Test
    void getHomeSuggestion_shouldFallbackToCuratedTemplateAndAvoidImmediateRepetition() {
        UUID accountHolderId = UUID.randomUUID();
        AccountHolder accountHolder = AccountHolder.builder()
                .id(accountHolderId)
                .serviceCountryCode("BJ")
                .build();

        prayerDailyCompletionRepository.save(PrayerDailyCompletion.builder()
                .dailyPrayerId(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .actorUserId(accountHolderId)
                .prayerDate(LocalDate.now(ZoneId.of(TEST_TIMEZONE)))
                .timezone(TEST_TIMEZONE)
                .responseType(PrayerDailyPrayerResponseType.PRAYED)
                .completedAt(OffsetDateTime.now())
                .build());

        seedTemplate("PROVISION_CLIENTS", PrayerSuggestionCategory.PROVISION, 10);
        seedTemplate("BUSINESS_WISDOM_PLANNING", PrayerSuggestionCategory.BUSINESS_WISDOM, 20);
        seedTemplate("UNITY_RELATIONSHIPS", PrayerSuggestionCategory.UNITY, 30);

        List<PrayerSuggestionTemplate> ordered = prayerSuggestionService.orderTemplatesForContext(
                prayerSuggestionTemplateRepository.findByActiveTrueOrderBySortOrderAscIdAsc(),
                false,
                true,
                true
        );

        assertEquals(PrayerSuggestionCategory.PROVISION, ordered.getFirst().getCategory());

        LocalDate today = LocalDate.now(ZoneId.of(TEST_TIMEZONE));
        PrayerSuggestionTemplate dayOne = prayerSuggestionService.selectTemplateForDate(accountHolderId, today, ordered);
        PrayerSuggestionTemplate dayTwo = prayerSuggestionService.selectTemplateForDate(accountHolderId, today.plusDays(1), ordered);

        assertNotEquals(dayOne.getTemplateKey(), dayTwo.getTemplateKey());

        PrayerHomeSuggestionResponse suggestion = prayerSuggestionService.getHomeSuggestion(
                accountHolder,
                false,
                true,
                true
        );

        assertEquals(PrayerHomeSuggestionSourceType.CURATED_TEMPLATE, suggestion.sourceType());
        assertNotNull(suggestion.deepLink());
        assertNotNull(suggestion.selectionReason());
    }

    private void seedTemplate(String templateKey, PrayerSuggestionCategory category, int sortOrder) {
        prayerSuggestionTemplateRepository.save(PrayerSuggestionTemplate.builder()
                .templateKey(templateKey)
                .title(templateKey)
                .content("Priere " + templateKey)
                .category(category)
                .tagsCsv("business,faith")
                .audioStatus(PrayerSuggestionAudioStatus.NONE)
                .active(true)
                .sortOrder(sortOrder)
                .build());
    }
}
