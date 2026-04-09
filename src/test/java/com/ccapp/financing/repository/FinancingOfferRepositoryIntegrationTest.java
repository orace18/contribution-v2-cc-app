package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.enums.FinancialProductType;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FinancingOfferRepositoryIntegrationTest {

    @Autowired
    private FinancingOfferRepository financingOfferRepository;

    @Test
    void search_shouldReturnResultsWhenCountryCodeFilterIsNull() {
        seedOffer("Offer CI", "CI", OffsetDateTime.parse("2026-04-09T10:00:00Z"));
        seedOffer("Offer GH", "GH", OffsetDateTime.parse("2026-04-09T09:00:00Z"));

        Page<FinancingOffer> result = financingOfferRepository.search(
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements());
        assertEquals("Offer CI", result.getContent().get(0).getName());
        assertEquals("Offer GH", result.getContent().get(1).getName());
    }

    @Test
    void search_shouldApplyCountryCodeFilterCaseInsensitively() {
        seedOffer("Offer CI", "CI", OffsetDateTime.parse("2026-04-09T08:00:00Z"));
        seedOffer("Offer GH", "GH", OffsetDateTime.parse("2026-04-09T07:00:00Z"));

        Page<FinancingOffer> result = financingOfferRepository.search(
                null,
                null,
                "ci",
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("Offer CI", result.getContent().get(0).getName());
    }

    private FinancingOffer seedOffer(String name, String countryCode, OffsetDateTime createdAt) {
        return financingOfferRepository.save(FinancingOffer.builder()
                .partnerId(UUID.randomUUID())
                .name(name)
                .description("Offer repository test")
                .type(FinancialProductType.CREDIT)
                .countryCode(countryCode)
                .minAmount(new BigDecimal("1000.0000"))
                .maxAmount(new BigDecimal("5000.0000"))
                .interestRate(new BigDecimal("12.50"))
                .durationMonths(12)
                .currency("XOF")
                .status(FinancingOfferStatus.ACTIVE)
                .createdAt(createdAt)
                .build());
    }
}
