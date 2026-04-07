package com.ccapp.prayer.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerSuggestionTemplate;
import com.ccapp.prayer.domain.enums.PrayerDeepLinkRouteKey;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.domain.enums.PrayerHomeSuggestionSourceType;
import com.ccapp.prayer.domain.enums.PrayerSuggestionAudioStatus;
import com.ccapp.prayer.domain.enums.PrayerSuggestionCategory;
import com.ccapp.prayer.dto.InternalDeepLinkResponse;
import com.ccapp.prayer.dto.PrayerHomeSuggestionResponse;
import com.ccapp.prayer.repository.PrayerDailyCompletionRepository;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import com.ccapp.prayer.repository.PrayerSuggestionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrayerSuggestionService {

    private final PrayerDailyContentRepository prayerDailyContentRepository;
    private final PrayerDailyCompletionRepository prayerDailyCompletionRepository;
    private final PrayerSuggestionTemplateRepository prayerSuggestionTemplateRepository;
    private final PrayerTimezoneResolver prayerTimezoneResolver;

    @Transactional(readOnly = true)
    public PrayerHomeSuggestionResponse getHomeSuggestion(AccountHolder accountHolder,
                                                          boolean hasCircle,
                                                          boolean hasActiveMembership,
                                                          boolean hasFinancingNeed) {
        PrayerTimezoneResolver.PrayerLocalContext localContext = prayerTimezoneResolver.resolve(accountHolder, null);
        PrayerDailyContent currentDaily = prayerDailyContentRepository
                .findByPrayerDateAndStatus(localContext.localDate(), PrayerDailyPrayerStatus.PUBLISHED)
                .orElse(null);
        boolean completedToday = prayerDailyCompletionRepository
                .findByAccountHolderIdAndPrayerDate(accountHolder.getId(), localContext.localDate())
                .isPresent();

        if (currentDaily != null && !completedToday) {
            return new PrayerHomeSuggestionResponse(
                    currentDaily.getId(),
                    PrayerHomeSuggestionSourceType.DAILY_PRAYER,
                    normalizeDailyTitle(currentDaily.getTitle()),
                    currentDaily.getContent(),
                    primaryCategory(hasCircle, hasActiveMembership, hasFinancingNeed),
                    List.of("daily-prayer"),
                    "Ta priere du jour est prete pour nourrir ta foi et ton activite.",
                    PrayerSuggestionAudioStatus.NONE,
                    null,
                    new InternalDeepLinkResponse(
                            PrayerDeepLinkRouteKey.PRAYER_DAY,
                            "Prier maintenant",
                            Collections.emptyMap()
                    )
            );
        }

        List<PrayerSuggestionTemplate> orderedTemplates = orderTemplatesForContext(
                prayerSuggestionTemplateRepository.findByActiveTrueOrderBySortOrderAscIdAsc(),
                hasCircle,
                hasActiveMembership,
                hasFinancingNeed
        );
        if (orderedTemplates.isEmpty()) {
            return null;
        }

        PrayerSuggestionTemplate selected = selectTemplateForDate(
                accountHolder.getId(),
                localContext.localDate(),
                orderedTemplates
        );
        return new PrayerHomeSuggestionResponse(
                selected.getId(),
                PrayerHomeSuggestionSourceType.CURATED_TEMPLATE,
                selected.getTitle(),
                selected.getContent(),
                selected.getCategory(),
                parseTags(selected.getTagsCsv()),
                selectionReason(selected.getCategory(), hasCircle, hasActiveMembership, hasFinancingNeed),
                selected.getAudioStatus(),
                selected.getAudioUrl(),
                new InternalDeepLinkResponse(
                        PrayerDeepLinkRouteKey.PRAYER_DAY,
                        "Recevoir cette priere",
                        Collections.emptyMap()
                )
        );
    }

    List<PrayerSuggestionTemplate> orderTemplatesForContext(List<PrayerSuggestionTemplate> templates,
                                                            boolean hasCircle,
                                                            boolean hasActiveMembership,
                                                            boolean hasFinancingNeed) {
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }

        List<PrayerSuggestionCategory> categoryPriority = categoryPriority(
                hasCircle,
                hasActiveMembership,
                hasFinancingNeed
        );
        return templates.stream()
                .sorted(Comparator
                        .comparingInt((PrayerSuggestionTemplate template) -> categoryPriority.indexOf(template.getCategory()))
                        .thenComparingInt(PrayerSuggestionTemplate::getSortOrder)
                        .thenComparing(PrayerSuggestionTemplate::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    PrayerSuggestionTemplate selectTemplateForDate(UUID accountHolderId,
                                                   LocalDate localDate,
                                                   List<PrayerSuggestionTemplate> orderedTemplates) {
        if (orderedTemplates == null || orderedTemplates.isEmpty()) {
            throw new IllegalArgumentException("orderedTemplates must not be empty");
        }

        int size = orderedTemplates.size();
        int startIndex = Math.floorMod(Objects.hash(accountHolderId, localDate), size);
        Set<String> blockedTemplateKeys = Set.of(
                orderedTemplates.get(Math.floorMod(Objects.hash(accountHolderId, localDate.minusDays(1)), size)).getTemplateKey(),
                orderedTemplates.get(Math.floorMod(Objects.hash(accountHolderId, localDate.minusDays(2)), size)).getTemplateKey()
        );

        for (int offset = 0; offset < size; offset++) {
            PrayerSuggestionTemplate candidate = orderedTemplates.get((startIndex + offset) % size);
            if (!blockedTemplateKeys.contains(candidate.getTemplateKey())) {
                return candidate;
            }
        }
        return orderedTemplates.get(startIndex);
    }

    private List<PrayerSuggestionCategory> categoryPriority(boolean hasCircle,
                                                            boolean hasActiveMembership,
                                                            boolean hasFinancingNeed) {
        List<PrayerSuggestionCategory> ordered = new ArrayList<>();
        if (hasFinancingNeed) {
            appendIfMissing(ordered, PrayerSuggestionCategory.PROVISION);
            appendIfMissing(ordered, PrayerSuggestionCategory.BUSINESS_WISDOM);
        }
        if (!hasCircle) {
            appendIfMissing(ordered, PrayerSuggestionCategory.UNITY);
            appendIfMissing(ordered, PrayerSuggestionCategory.COURAGE);
        }
        if (!hasActiveMembership) {
            appendIfMissing(ordered, PrayerSuggestionCategory.DISCIPLINE);
        }
        appendIfMissing(ordered, PrayerSuggestionCategory.GRATITUDE);
        appendIfMissing(ordered, PrayerSuggestionCategory.BUSINESS_WISDOM);
        for (PrayerSuggestionCategory category : PrayerSuggestionCategory.values()) {
            appendIfMissing(ordered, category);
        }
        return List.copyOf(ordered);
    }

    private PrayerSuggestionCategory primaryCategory(boolean hasCircle,
                                                     boolean hasActiveMembership,
                                                     boolean hasFinancingNeed) {
        return categoryPriority(hasCircle, hasActiveMembership, hasFinancingNeed).getFirst();
    }

    private String normalizeDailyTitle(String title) {
        return title == null || title.isBlank() ? "Priere du jour" : title.trim();
    }

    private List<String> parseTags(String tagsCsv) {
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagsCsv.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
    }

    private String selectionReason(PrayerSuggestionCategory category,
                                   boolean hasCircle,
                                   boolean hasActiveMembership,
                                   boolean hasFinancingNeed) {
        if (hasFinancingNeed && category == PrayerSuggestionCategory.PROVISION) {
            return "Une priere de provision pour remettre tes besoins financiers entre les mains de Dieu.";
        }
        if (!hasCircle && category == PrayerSuggestionCategory.UNITY) {
            return "Une priere d unite pour t aider a avancer avec les bonnes personnes.";
        }
        if (!hasActiveMembership && category == PrayerSuggestionCategory.DISCIPLINE) {
            return "Une priere de discipline pour garder un rythme fidele dans ton parcours.";
        }
        if (category == PrayerSuggestionCategory.COURAGE) {
            return "Une priere de courage pour diriger ton activite avec paix et assurance.";
        }
        if (category == PrayerSuggestionCategory.GRATITUDE) {
            return "Une priere de reconnaissance pour garder un coeur aligne sur l essentiel.";
        }
        return "Une priere choisie pour fortifier ta sagesse spirituelle et entrepreneuriale aujourd hui.";
    }

    private void appendIfMissing(List<PrayerSuggestionCategory> ordered, PrayerSuggestionCategory category) {
        if (!ordered.contains(category)) {
            ordered.add(category);
        }
    }
}
