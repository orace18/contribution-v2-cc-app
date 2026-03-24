package com.ccapp.financing.service;

import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.domain.enums.FinancialProductType;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import com.ccapp.financing.domain.enums.OfferRequirementType;
import com.ccapp.financing.domain.enums.OfferVerificationType;
import com.ccapp.financing.dto.CreateFinancingOfferRequest;
import com.ccapp.financing.dto.FinancingOfferResponse;
import com.ccapp.financing.dto.OfferCountryConfigItemRequest;
import com.ccapp.financing.dto.OfferCountryConfigUpdateRequest;
import com.ccapp.financing.dto.OfferRequirementResponse;
import com.ccapp.financing.dto.OfferRequirementUpsertRequest;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({FinancingOfferAdminService.class, OfferRequirementService.class, TestJacksonAndCachingConfig.class})
class FinancingOfferAdminServiceIntegrationTest {

    @Autowired
    private FinancingOfferAdminService financingOfferAdminService;
    @Autowired
    private OfferRequirementService offerRequirementService;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private FinancialPartnerRepository financialPartnerRepository;

    @Test
    void createOfferAndConfigureCountries_shouldPersistRequirementsAndCountryConfig() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("BJ")
                .name("Benin")
                .currency("XOF")
                .active(true)
                .configured(true)
                .fallback(false)
                .build());
        FinancialPartner partner = financialPartnerRepository.save(FinancialPartner.builder()
                .name("Partner Two")
                .type(FinancialPartnerType.BANK)
                .countryCode("BJ")
                .currency("XOF")
                .status(FinancialPartnerStatus.ACTIVE)
                .build());

        FinancingOfferResponse created = financingOfferAdminService.createOffer(new CreateFinancingOfferRequest(
                partner.getId(),
                FinancialProductType.CREDIT,
                "SME Growth",
                "Offer for SMEs",
                "BJ",
                new BigDecimal("100.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4.50"),
                12,
                "xof",
                "{\"originationFeePercent\":2}",
                "{\"minimumScore\":65}",
                "[\"ID_CARD\"]",
                "{\"segment\":\"SME\"}",
                FinancingOfferStatus.ACTIVE,
                List.of(
                        new OfferRequirementUpsertRequest(OfferRequirementType.KYC_DOCUMENT, true, OfferVerificationType.DOCUMENT_UPLOAD),
                        new OfferRequirementUpsertRequest(OfferRequirementType.BANK_STATEMENT, true, OfferVerificationType.MANUAL)
                )
        ));
        assertEquals(2, created.requirements().size());

        var configuredCountries = financingOfferAdminService.updateOfferCountries(
                created.id(),
                new OfferCountryConfigUpdateRequest(List.of(
                        new OfferCountryConfigItemRequest("BJ", true, "XOF")
                ))
        );
        assertEquals(1, configuredCountries.size());
        assertEquals("BJ", configuredCountries.get(0).countryCode());

        List<OfferRequirementResponse> requirements = offerRequirementService.listRequirements(created.id());
        assertEquals(2, requirements.size());
    }
}
