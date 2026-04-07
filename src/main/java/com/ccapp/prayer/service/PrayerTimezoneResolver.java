package com.ccapp.prayer.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Component
class PrayerTimezoneResolver {

    private static final String UTC = "UTC";

    private static final Map<String, String> COUNTRY_TIMEZONES = Map.ofEntries(
            Map.entry("BJ", "Africa/Porto-Novo"),
            Map.entry("TG", "Africa/Porto-Novo"),
            Map.entry("CI", "Africa/Abidjan"),
            Map.entry("SN", "Africa/Dakar"),
            Map.entry("ML", "Africa/Bamako"),
            Map.entry("BF", "Africa/Ouagadougou"),
            Map.entry("NE", "Africa/Niamey"),
            Map.entry("GW", "Africa/Bissau"),
            Map.entry("CM", "Africa/Douala"),
            Map.entry("CF", "Africa/Bangui"),
            Map.entry("CG", "Africa/Brazzaville"),
            Map.entry("GA", "Africa/Libreville"),
            Map.entry("TD", "Africa/Ndjamena"),
            Map.entry("CD", "Africa/Kinshasa")
    );

    PrayerLocalContext resolve(AccountHolder accountHolder, String requestedTimezone) {
        ZoneId zoneId = resolveZoneId(accountHolder, requestedTimezone);
        return new PrayerLocalContext(
                zoneId.getId(),
                LocalDate.now(zoneId),
                OffsetDateTime.now(zoneId)
        );
    }

    private ZoneId resolveZoneId(AccountHolder accountHolder, String requestedTimezone) {
        if (requestedTimezone != null && !requestedTimezone.isBlank()) {
            try {
                return ZoneId.of(requestedTimezone.trim());
            } catch (Exception ex) {
                throw new BusinessException("PRAYER_TIMEZONE_INVALID");
            }
        }

        if (accountHolder != null && accountHolder.getServiceCountryCode() != null) {
            String mapped = COUNTRY_TIMEZONES.get(accountHolder.getServiceCountryCode().trim().toUpperCase());
            if (mapped != null) {
                return ZoneId.of(mapped);
            }
        }
        return ZoneId.of(UTC);
    }

    record PrayerLocalContext(
            String timezone,
            LocalDate localDate,
            OffsetDateTime now
    ) {
    }
}
