package com.ccapp.mobile.earnings.service;

import com.ccapp.credit.domain.entity.CreditEligibility;
import com.ccapp.credit.repository.CreditEligibilityRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mobile.earnings.dto.MobileEarningHistoryItemResponse;
import com.ccapp.mobile.earnings.dto.MobileEarningsSummaryResponse;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(MobileEarningsService.class)
class MobileEarningsServiceIntegrationTest {

    @Autowired
    private MobileEarningsService mobileEarningsService;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;
    @Autowired
    private CreditEligibilityRepository creditEligibilityRepository;

    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private AccountHolderService accountHolderService;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
    }

    @Test
    void shouldAggregateSummaryAndHistory() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = createAccountHolder();

        creditEligibilityRepository.save(CreditEligibility.builder()
                .accountHolderId(accountHolderId)
                .rmsScore(new BigDecimal("88.00"))
                .circleScore(new BigDecimal("70.00"))
                .eligibilityScore(new BigDecimal("84.00"))
                .maxEligibleAmount(new BigDecimal("100000"))
                .build());

        mlmCommissionRepository.save(MlmCommission.builder()
                .operationId(UUID.randomUUID())
                .beneficiaryAccountHolderId(accountHolderId)
                .amount(new BigDecimal("5000"))
                .currency("XOF")
                .source("WEEKLY_GROUP_BONUS")
                .commissionType(CommissionType.GROUP_BONUS)
                .generation(0)
                .status(CommissionStatus.PAID)
                .paidAt(OffsetDateTime.now().minusDays(1))
                .createdAt(OffsetDateTime.now().minusDays(1))
                .build());

        mlmCommissionRepository.save(MlmCommission.builder()
                .operationId(UUID.randomUUID())
                .beneficiaryAccountHolderId(accountHolderId)
                .amount(new BigDecimal("15000"))
                .currency("XOF")
                .source("SUBSCRIPTION_UNILEVEL")
                .commissionType(CommissionType.SUBSCRIPTION)
                .generation(2)
                .status(CommissionStatus.PENDING)
                .createdAt(OffsetDateTime.now().minusHours(2))
                .build());

        MobileEarningsSummaryResponse summary = mobileEarningsService.getSummary(actorUserId, accountHolderId);
        assertTrue(summary.totalRms().compareTo(new BigDecimal("20000")) == 0);
        assertTrue(summary.weeklyGrowth().compareTo(new BigDecimal("20000")) == 0);
        assertTrue(summary.potential().compareTo(new BigDecimal("100000")) == 0);

        List<MobileEarningHistoryItemResponse> history = mobileEarningsService.getHistory(actorUserId, accountHolderId);
        assertEquals(2, history.size());
        assertEquals("LEVEL_2_COMMISSION", history.get(0).type());
        assertEquals(CommissionStatus.PENDING, history.get(0).status());
        assertEquals("TEAM_BONUS", history.get(1).type());
        assertEquals(CommissionStatus.PAID, history.get(1).status());
    }

    private UUID createAccountHolder() {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName("Leader")
                .lastName("Root")
                .phoneNumber("+22997001000")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }
}
