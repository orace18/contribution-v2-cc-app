package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.enums.FinancialProductType;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
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
class LoanApplicationRepositoryIntegrationTest {

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private FinancingOfferRepository financingOfferRepository;

    @Test
    void searchForAdmin_shouldReturnResultsWhenCountryCodeFilterIsNull() {
        FinancingOffer ciOffer = seedOffer("CI");
        FinancingOffer ghOffer = seedOffer("GH");
        seedApplication(ciOffer.getId(), LoanApplicationStatus.SUBMITTED, OffsetDateTime.parse("2026-04-09T10:00:00Z"));
        seedApplication(ghOffer.getId(), LoanApplicationStatus.APPROVED, OffsetDateTime.parse("2026-04-09T09:00:00Z"));

        Page<LoanApplication> result = loanApplicationRepository.searchForAdmin(
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements());
        assertEquals(LoanApplicationStatus.SUBMITTED, result.getContent().get(0).getStatus());
        assertEquals(LoanApplicationStatus.APPROVED, result.getContent().get(1).getStatus());
    }

    @Test
    void searchForAdmin_shouldApplyCountryCodeFilterCaseInsensitively() {
        FinancingOffer ciOffer = seedOffer("CI");
        FinancingOffer ghOffer = seedOffer("GH");
        seedApplication(ciOffer.getId(), LoanApplicationStatus.SUBMITTED, OffsetDateTime.parse("2026-04-09T08:00:00Z"));
        seedApplication(ghOffer.getId(), LoanApplicationStatus.APPROVED, OffsetDateTime.parse("2026-04-09T07:00:00Z"));

        Page<LoanApplication> result = loanApplicationRepository.searchForAdmin(
                null,
                null,
                "ci",
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(LoanApplicationStatus.SUBMITTED, result.getContent().get(0).getStatus());
    }

    private FinancingOffer seedOffer(String countryCode) {
        return financingOfferRepository.save(FinancingOffer.builder()
                .partnerId(UUID.randomUUID())
                .name("Offer " + countryCode)
                .description("Loan application repository test")
                .type(FinancialProductType.CREDIT)
                .countryCode(countryCode)
                .minAmount(new BigDecimal("1000.0000"))
                .maxAmount(new BigDecimal("5000.0000"))
                .interestRate(new BigDecimal("10.00"))
                .durationMonths(12)
                .currency("XOF")
                .status(FinancingOfferStatus.ACTIVE)
                .createdAt(OffsetDateTime.parse("2026-04-09T06:00:00Z"))
                .build());
    }

    private LoanApplication seedApplication(UUID offerId,
                                            LoanApplicationStatus status,
                                            OffsetDateTime createdAt) {
        return loanApplicationRepository.save(LoanApplication.builder()
                .offerId(offerId)
                .accountHolderId(UUID.randomUUID())
                .requestedAmount(new BigDecimal("1500.0000"))
                .approvedAmount(new BigDecimal("1200.0000"))
                .status(status)
                .partnerReference("partner-ref-" + UUID.randomUUID())
                .decisionNote("Repository test")
                .configSnapshot("{}")
                .configVersion(1)
                .createdAt(createdAt)
                .build());
    }
}
