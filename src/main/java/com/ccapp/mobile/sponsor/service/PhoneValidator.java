package com.ccapp.mobile.sponsor.service;

import com.ccapp.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Validates a phone number against the expected subscriber digit count for a given country.
 *
 * The phone is expected in international format (+{callingCode}{subscriberNumber}).
 * If no rule exists for the given country, validation is skipped (open for unknown markets).
 *
 * Add rules here as new countries are onboarded.
 */
@Component
public class PhoneValidator {

    /**
     * country ISO code → (calling code, expected subscriber digit count)
     */
    private static final Map<String, CountryPhoneRule> RULES = Map.ofEntries(
            Map.entry("BJ", new CountryPhoneRule("229", 10)),  // Bénin
            Map.entry("CI", new CountryPhoneRule("225", 10)),  // Côte d'Ivoire
            Map.entry("CM", new CountryPhoneRule("237",  9)),  // Cameroun
            Map.entry("SN", new CountryPhoneRule("221",  9)),  // Sénégal
            Map.entry("TG", new CountryPhoneRule("228",  8)),  // Togo
            Map.entry("GH", new CountryPhoneRule("233",  9)),  // Ghana
            Map.entry("NG", new CountryPhoneRule("234", 10)),  // Nigeria
            Map.entry("CD", new CountryPhoneRule("243",  9)),  // RDC
            Map.entry("CG", new CountryPhoneRule("242",  9)),  // Congo
            Map.entry("ML", new CountryPhoneRule("223",  8)),  // Mali
            Map.entry("BF", new CountryPhoneRule("226",  8)),  // Burkina Faso
            Map.entry("NE", new CountryPhoneRule("227",  8))   // Niger
    );

    /**
     * Validates that {@code normalizedPhone} (already in +{international} format) matches
     * the expected format for {@code countryCode}.
     *
     * @throws BusinessException with code INVALID_PHONE_FORMAT if the phone is malformed.
     */
    public void validate(String normalizedPhone, String countryCode) {
        if (countryCode == null || countryCode.isBlank()) return;

        CountryPhoneRule rule = RULES.get(countryCode.toUpperCase());
        if (rule == null) return; // no rule defined — skip

        String expected = "+" + rule.callingCode();
        if (!normalizedPhone.startsWith(expected)) {
            throw new BusinessException("INVALID_PHONE_FORMAT");
        }

        String subscriber = normalizedPhone.substring(expected.length());
        if (!subscriber.matches("\\d+") || subscriber.length() != rule.subscriberLength()) {
            throw new BusinessException("INVALID_PHONE_FORMAT");
        }
    }

    /**
     * Normalizes a raw phone number into E.164 format for the provided country when possible.
     * <p>
     * Rules:
     * <ul>
     *     <li>If the value already starts with {@code +}, it is returned unchanged.</li>
     *     <li>If the value starts with {@code 00}, the prefix is converted to {@code +}.</li>
     *     <li>Otherwise, known country calling codes are applied when missing.</li>
     *     <li>If the country is unknown, a simple {@code +} prefix is used as a safe fallback.</li>
     * </ul>
     */
    public static String normalizeToE164(String rawPhone, String countryCode) {
        if (rawPhone == null) {
            return null;
        }

        String trimmedPhone = rawPhone.trim();
        if (trimmedPhone.isEmpty()) {
            return trimmedPhone;
        }
        if (trimmedPhone.startsWith("+")) {
            return trimmedPhone;
        }

        String digitsOnly = trimmedPhone.replaceAll("\\D", "");
        if (digitsOnly.isEmpty()) {
            return trimmedPhone;
        }
        if (digitsOnly.startsWith("00") && digitsOnly.length() > 2) {
            return "+" + digitsOnly.substring(2);
        }

        CountryPhoneRule rule = countryCode == null || countryCode.isBlank()
                ? null
                : RULES.get(countryCode.trim().toUpperCase(Locale.ROOT));
        if (rule == null) {
            return "+" + digitsOnly;
        }
        if (digitsOnly.startsWith(rule.callingCode())) {
            return "+" + digitsOnly;
        }
        return "+" + rule.callingCode() + digitsOnly;
    }

    record CountryPhoneRule(String callingCode, int subscriberLength) {}
}
