package com.ccapp.prayer.service;

import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.repository.PrayerDailyContentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerBusinessDailyCatalogSeedService {

    private static final String RESOURCE_PATH = "classpath:seed/prayers_business_seed_fr.json";
    private static final LocalDate CATALOG_START_DATE = LocalDate.of(2026, 1, 1);

    private final PrayerDailyContentRepository prayerDailyContentRepository;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeedResult seedBusinessCatalog() {
        Resource resource = resourceLoader.getResource(RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("Prayer business catalog seed skipped: resource not found at {}", RESOURCE_PATH);
            return new SeedResult(0, 0, 0);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode prayers = root.isArray() ? root : root.path("prayers");
            if (!prayers.isArray()) {
                throw new IllegalStateException("Prayer business seed must contain a prayers array");
            }

            UUID creatorAdminUserId = resolveCreatorAdminUserId();
            OffsetDateTime now = OffsetDateTime.now();
            Set<LocalDate> reservedDates = new HashSet<>();
            int inserted = 0;
            int updated = 0;

            for (JsonNode seedPrayer : prayers) {
                String code = normalizeCode(text(seedPrayer, "code", null));
                if (code == null) {
                    throw new IllegalStateException("Prayer business seed contains a row without code");
                }

                Optional<PrayerDailyContent> existing = prayerDailyContentRepository.findByCode(code);
                boolean isNew = existing.isEmpty();
                PrayerDailyContent dailyContent = existing.orElseGet(PrayerDailyContent::new);
                applySeedPrayer(dailyContent, seedPrayer, code, creatorAdminUserId, now, reservedDates);
                prayerDailyContentRepository.save(dailyContent);

                if (isNew) {
                    inserted++;
                } else {
                    updated++;
                }
            }

            return new SeedResult(inserted + updated, inserted, updated);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to seed prayer business catalog", ex);
        }
    }

    private void applySeedPrayer(PrayerDailyContent dailyContent,
                                 JsonNode seedPrayer,
                                 String code,
                                 UUID creatorAdminUserId,
                                 OffsetDateTime now,
                                 Set<LocalDate> reservedDates) {
        int priority = integer(seedPrayer, "priority", 1000);
        boolean published = bool(seedPrayer, "published", true);

        dailyContent.setCode(code);
        dailyContent.setPrayerDate(resolvePrayerDate(dailyContent, priority, reservedDates));
        dailyContent.setTitle(text(seedPrayer, "title", code));
        dailyContent.setScriptureReference(text(seedPrayer, "scriptureReference", null));
        dailyContent.setScriptureText(text(seedPrayer, "scriptureText", null));
        dailyContent.setShortContent(text(seedPrayer, "shortContent", dailyContent.getTitle()));
        dailyContent.setContent(requiredText(seedPrayer, "content", code));
        dailyContent.setDeclaration(text(seedPrayer, "declaration", null));
        dailyContent.setCategory(text(seedPrayer, "category", null));
        dailyContent.setTheme(text(seedPrayer, "theme", null));
        dailyContent.setTone(text(seedPrayer, "tone", "simple et inspirant"));
        dailyContent.setAudience(text(seedPrayer, "audience", "entrepreneurs et travailleurs Afrique francophone"));
        dailyContent.setTagsCsv(tagsCsv(seedPrayer.path("tags")));
        dailyContent.setLanguage(text(seedPrayer, "language", "fr"));
        dailyContent.setCountryScope(text(seedPrayer, "countryScope", "AFRICA_FR"));
        dailyContent.setFeatured(bool(seedPrayer, "featured", false));
        dailyContent.setPriority(priority);
        dailyContent.setEstimatedReadSeconds(integer(seedPrayer, "estimatedReadSeconds", 45));
        dailyContent.setAudioStatus(text(seedPrayer, "audioStatus", "NONE"));
        dailyContent.setStatus(published ? PrayerDailyPrayerStatus.PUBLISHED : PrayerDailyPrayerStatus.DRAFT);
        dailyContent.setPublishedAt(published
                ? (dailyContent.getPublishedAt() == null ? now : dailyContent.getPublishedAt())
                : null);

        if (dailyContent.getCreatedByAdminUserId() == null) {
            dailyContent.setCreatedByAdminUserId(creatorAdminUserId);
        }
    }

    private LocalDate resolvePrayerDate(PrayerDailyContent dailyContent, int priority, Set<LocalDate> reservedDates) {
        if (dailyContent.getPrayerDate() != null) {
            reservedDates.add(dailyContent.getPrayerDate());
            return dailyContent.getPrayerDate();
        }

        LocalDate candidate = CATALOG_START_DATE.plusDays(Math.max(0, priority - 1L));
        while (reservedDates.contains(candidate) || isDateTakenByAnotherPrayer(candidate, dailyContent.getId())) {
            candidate = candidate.plusDays(1);
        }
        reservedDates.add(candidate);
        return candidate;
    }

    private boolean isDateTakenByAnotherPrayer(LocalDate candidate, UUID currentId) {
        return prayerDailyContentRepository.findByPrayerDate(candidate)
                .filter(existing -> !Objects.equals(existing.getId(), currentId))
                .isPresent();
    }

    private UUID resolveCreatorAdminUserId() {
        return adminUserRepository.findAllByOrderByCreatedAtDesc().stream()
                .findFirst()
                .map(AdminUser::getId)
                .orElse(null);
    }

    private String requiredText(JsonNode node, String field, String code) {
        String value = text(node, field, null);
        if (value == null) {
            throw new IllegalStateException("Prayer business seed " + code + " is missing " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private boolean bool(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asBoolean(defaultValue);
    }

    private int integer(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String tagsCsv(JsonNode tags) {
        if (!tags.isArray()) {
            return text(tags, "", null);
        }
        String csv = StreamSupport.stream(tags.spliterator(), false)
                .map(tag -> tag.asText("").trim())
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.joining(","));
        return csv.length() > 512 ? csv.substring(0, 512) : csv;
    }

    public record SeedResult(
            int processed,
            int inserted,
            int updated
    ) {
    }
}
