package com.ccapp.financing.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.dto.CreateFinancialPartnerRequest;
import com.ccapp.financing.dto.FinancialPartnerResponse;
import com.ccapp.financing.dto.UpdateFinancialPartnerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(FinancialPartnerAdminService.class)
class FinancialPartnerAdminServiceIntegrationTest {

    @Autowired
    private FinancialPartnerAdminService financialPartnerAdminService;
    @Autowired
    private CountryRepository countryRepository;

    @Test
    void createListUpdateAndGetPartner_shouldWorkWithPaginationAndFilters() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("BJ")
                .name("Benin")
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build());

        FinancialPartnerResponse created = financialPartnerAdminService.createPartner(new CreateFinancialPartnerRequest(
                "Seed Finance",
                FinancialPartnerType.FUND,
                "bj",
                "xof",
                "ops@seed.example",
                "Seed finance partner",
                FinancialPartnerStatus.ACTIVE,
                "https://seed.example/api"
        ));
        assertEquals("BJ", created.countryCode());
        assertEquals("Seed Finance", created.name());
        assertEquals("XOF", created.currency());
        assertEquals("ops@seed.example", created.contactEmail());
        assertEquals("Seed finance partner", created.description());

        PageResponse<FinancialPartnerResponse> page = financialPartnerAdminService.listPartners(
                FinancialPartnerStatus.ACTIVE,
                FinancialPartnerType.FUND,
                "BJ",
                "seed",
                0,
                20
        );
        assertEquals(1, page.items().size());

        FinancialPartnerResponse updated = financialPartnerAdminService.updatePartner(
                created.id(),
                new UpdateFinancialPartnerRequest(
                        "Seed Finance Group",
                        FinancialPartnerType.FUND,
                        "BJ",
                        "eur",
                        "support@seed.example",
                        "Updated seed finance partner",
                        FinancialPartnerStatus.INACTIVE,
                        "https://seed.example/v2"
                )
        );
        assertEquals(FinancialPartnerStatus.INACTIVE, updated.status());
        assertEquals("Seed Finance Group", updated.name());
        assertEquals("EUR", updated.currency());
        assertEquals("support@seed.example", updated.contactEmail());
        assertEquals("Updated seed finance partner", updated.description());

        FinancialPartnerResponse loaded = financialPartnerAdminService.getPartner(created.id());
        assertEquals(created.id(), loaded.id());
        assertEquals("https://seed.example/v2", loaded.apiEndpoint());
        assertEquals("EUR", loaded.currency());
        assertEquals("support@seed.example", loaded.contactEmail());
        assertEquals("Updated seed finance partner", loaded.description());
    }
}
