package com.ccapp.identity.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.dto.OnboardingCatalogItemResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OnboardingCatalogService {

    private static final List<OnboardingCatalogItemResponse> ACTIVITY_SECTORS = List.of(
            new OnboardingCatalogItemResponse("AGRICULTURE", "Agriculture"),
            new OnboardingCatalogItemResponse("COMMERCE", "Commerce"),
            new OnboardingCatalogItemResponse("ELEVAGE", "Elevage"),
            new OnboardingCatalogItemResponse("ARTISANAT", "Artisanat"),
            new OnboardingCatalogItemResponse("RESTAURATION", "Restauration"),
            new OnboardingCatalogItemResponse("TRANSPORT", "Transport"),
            new OnboardingCatalogItemResponse("SERVICES", "Services"),
            new OnboardingCatalogItemResponse("EDUCATION", "Education"),
            new OnboardingCatalogItemResponse("SANTE", "Sante"),
            new OnboardingCatalogItemResponse("TECHNOLOGIE", "Technologie"),
            new OnboardingCatalogItemResponse("AUTRE", "Autre")
    );

    private static final List<OnboardingCatalogItemResponse> INCOME_BRACKETS = List.of(
            new OnboardingCatalogItemResponse("LESS_THAN_100K", "Moins de 100 000 CFA"),
            new OnboardingCatalogItemResponse("BETWEEN_100K_300K", "100 000 - 300 000 CFA"),
            new OnboardingCatalogItemResponse("BETWEEN_300K_600K", "300 000 - 600 000 CFA"),
            new OnboardingCatalogItemResponse("BETWEEN_600K_1M", "600 000 - 1 000 000 CFA"),
            new OnboardingCatalogItemResponse("ABOVE_1M", "Plus de 1 000 000 CFA")
    );

    private static final Set<String> ACTIVITY_SECTOR_CODES = ACTIVITY_SECTORS.stream()
            .map(OnboardingCatalogItemResponse::code)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> INCOME_BRACKET_CODES = INCOME_BRACKETS.stream()
            .map(OnboardingCatalogItemResponse::code)
            .collect(Collectors.toUnmodifiableSet());

    public List<OnboardingCatalogItemResponse> listActivitySectors() {
        return ACTIVITY_SECTORS;
    }

    public List<OnboardingCatalogItemResponse> listIncomeBrackets() {
        return INCOME_BRACKETS;
    }

    public String normalizeAndValidateActivitySectorCode(String code) {
        String normalized = normalizeCode(code);
        if (!ACTIVITY_SECTOR_CODES.contains(normalized)) {
            throw new BusinessException("Unsupported activity sector");
        }
        return normalized;
    }

    public String normalizeAndValidateIncomeBracketCode(String code) {
        String normalized = normalizeCode(code);
        if (!INCOME_BRACKET_CODES.contains(normalized)) {
            throw new BusinessException("Unsupported income bracket");
        }
        return normalized;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
