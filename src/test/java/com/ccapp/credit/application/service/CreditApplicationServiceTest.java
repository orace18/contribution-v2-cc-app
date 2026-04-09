package com.ccapp.credit.application.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.credit.application.domain.entity.CreditApplication;
import com.ccapp.credit.application.domain.enums.CreditApplicationStatus;
import com.ccapp.credit.application.dto.CreditApplicationProjectRequest;
import com.ccapp.credit.application.dto.CreateSeedCapitalCreditApplicationRequest;
import com.ccapp.credit.application.dto.CreditApplicationResponse;
import com.ccapp.credit.application.repository.CreditApplicationRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalCreditBridgeState;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalEligibilityState;
import com.ccapp.seedcapital.readiness.dto.SeedCapitalCreditCandidateResponse;
import com.ccapp.seedcapital.readiness.service.SeedCapitalCreditBridgeService;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsGateState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditApplicationServiceTest {

    @Mock
    private CreditApplicationRepository creditApplicationRepository;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private CountryContextService countryContextService;
    @Mock
    private SeedCapitalCreditBridgeService seedCapitalCreditBridgeService;

    @InjectMocks
    private CreditApplicationService creditApplicationService;

    @Test
    void createSeedCapitalApplication_shouldPersistSubmittedApplicationWhenBridgeAllows() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        CreditApplicationProjectRequest project = new CreditApplicationProjectRequest(
                "Boutique de vetements",
                "Commerce",
                "Achat de stock initial",
                "Stock + petit equipement"
        );
        CreateSeedCapitalCreditApplicationRequest request = new CreateSeedCapitalCreditApplicationRequest(
                circleId,
                new BigDecimal("250000.0000"),
                6,
                new BigDecimal("5.50"),
                project
        );
        when(accountHolderService.getById(accountHolderId)).thenReturn(AccountHolder.builder().id(accountHolderId).build());
        doNothing().when(accessControlService).assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        when(seedCapitalCreditBridgeService.requireCandidate(accountHolderId, circleId)).thenReturn(
                new SeedCapitalCreditCandidateResponse(
                        accountHolderId,
                        circleId,
                        true,
                        SeedCapitalCreditBridgeState.CANDIDATE_FOR_CREDIT_APPLICATION,
                        null,
                        List.of(),
                        SeedCapitalEligibilityState.ELIGIBLE,
                        new BigDecimal("90.00"),
                        List.of(),
                        List.of(),
                        ImfSavingsGateState.READY,
                        3,
                        3,
                        2,
                        2,
                        new BigDecimal("82.00"),
                        new BigDecimal("500000.0000"),
                        OffsetDateTime.now()
                )
        );
        when(creditApplicationRepository.existsByAccountHolderIdAndCircleIdAndProductTypeAndWorkflowStateIn(
                eq(accountHolderId),
                eq(circleId),
                any(),
                any()
        )).thenReturn(false);
        when(countryContextService.resolve(accountHolderId)).thenReturn(new CountryContext("BJ", "BJ", "XOF"));
        when(creditApplicationRepository.save(any(CreditApplication.class))).thenAnswer(invocation -> {
            CreditApplication application = invocation.getArgument(0);
            application.setId(UUID.randomUUID());
            application.setWorkflowState(CreditApplicationStatus.SUBMITTED);
            return application;
        });

        CreditApplicationResponse response = creditApplicationService.createSeedCapitalApplication(actorUserId, accountHolderId, request);

        ArgumentCaptor<CreditApplication> captor = ArgumentCaptor.forClass(CreditApplication.class);
        verify(creditApplicationRepository).save(captor.capture());
        CreditApplication saved = captor.getValue();
        assertEquals(circleId, saved.getCircleId());
        assertEquals("250000.0000", saved.getRequestedAmount().toPlainString());
        assertEquals("XOF", saved.getCurrency());
        assertEquals(CreditApplicationStatus.SUBMITTED, saved.getWorkflowState());
        assertEquals(SeedCapitalCreditBridgeState.CANDIDATE_FOR_CREDIT_APPLICATION, saved.getBridgeState());
        assertEquals("500000.0000", saved.getMaxEligibleAmount().toPlainString());
        assertEquals(ImfSavingsGateState.READY, saved.getSavingsGateState());
        assertEquals(3, saved.getReadyImfChannelsCount());
        assertEquals("Boutique de vetements", saved.getProjectName());
        assertEquals("Commerce", saved.getProjectSector());
        assertEquals("XOF", response.currency());
        assertEquals(CreditApplicationStatus.SUBMITTED, response.workflowState());
        assertEquals("Boutique de vetements", response.project().name());
        assertEquals("Stock + petit equipement", response.project().useOfFunds());
    }

    @Test
    void createSeedCapitalApplication_shouldBlockWhenRequestedAmountExceedsBridgeLimit() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        CreateSeedCapitalCreditApplicationRequest request = new CreateSeedCapitalCreditApplicationRequest(
                circleId,
                new BigDecimal("600000.0000"),
                null,
                null,
                null
        );
        when(accountHolderService.getById(accountHolderId)).thenReturn(AccountHolder.builder().id(accountHolderId).build());
        doNothing().when(accessControlService).assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        when(seedCapitalCreditBridgeService.requireCandidate(accountHolderId, circleId)).thenReturn(
                new SeedCapitalCreditCandidateResponse(
                        accountHolderId,
                        circleId,
                        true,
                        SeedCapitalCreditBridgeState.CANDIDATE_FOR_CREDIT_APPLICATION,
                        null,
                        List.of(),
                        SeedCapitalEligibilityState.ELIGIBLE,
                        new BigDecimal("90.00"),
                        List.of(),
                        List.of(),
                        ImfSavingsGateState.READY,
                        3,
                        3,
                        2,
                        2,
                        new BigDecimal("82.00"),
                        new BigDecimal("500000.0000"),
                        OffsetDateTime.now()
                )
        );
        when(creditApplicationRepository.existsByAccountHolderIdAndCircleIdAndProductTypeAndWorkflowStateIn(
                eq(accountHolderId),
                eq(circleId),
                any(),
                any()
        )).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditApplicationService.createSeedCapitalApplication(actorUserId, accountHolderId, request)
        );

        assertEquals("Requested amount exceeds current max eligible amount", exception.getMessage());
        verify(creditApplicationRepository, never()).save(any(CreditApplication.class));
    }

    @Test
    void createSeedCapitalApplication_shouldBlockDuplicateOpenApplication() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        CreateSeedCapitalCreditApplicationRequest request = new CreateSeedCapitalCreditApplicationRequest(
                circleId,
                new BigDecimal("250000.0000"),
                null,
                null,
                null
        );
        when(accountHolderService.getById(accountHolderId)).thenReturn(AccountHolder.builder().id(accountHolderId).build());
        doNothing().when(accessControlService).assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        when(seedCapitalCreditBridgeService.requireCandidate(accountHolderId, circleId)).thenReturn(
                new SeedCapitalCreditCandidateResponse(
                        accountHolderId,
                        circleId,
                        true,
                        SeedCapitalCreditBridgeState.CANDIDATE_FOR_CREDIT_APPLICATION,
                        null,
                        List.of(),
                        SeedCapitalEligibilityState.ELIGIBLE,
                        new BigDecimal("90.00"),
                        List.of(),
                        List.of(),
                        ImfSavingsGateState.READY,
                        3,
                        3,
                        2,
                        2,
                        new BigDecimal("82.00"),
                        new BigDecimal("500000.0000"),
                        OffsetDateTime.now()
                )
        );
        when(creditApplicationRepository.existsByAccountHolderIdAndCircleIdAndProductTypeAndWorkflowStateIn(
                eq(accountHolderId),
                eq(circleId),
                any(),
                any()
        )).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditApplicationService.createSeedCapitalApplication(actorUserId, accountHolderId, request)
        );

        assertEquals("An active seed capital credit application already exists for this circle", exception.getMessage());
        verify(creditApplicationRepository, never()).save(any(CreditApplication.class));
    }

    @Test
    void createSeedCapitalApplication_shouldEnforceBridgeAndStopWriteWhenCandidateIsNotAllowed() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        CreateSeedCapitalCreditApplicationRequest request = new CreateSeedCapitalCreditApplicationRequest(
                circleId,
                new BigDecimal("250000.0000"),
                null,
                null,
                null
        );
        when(accountHolderService.getById(accountHolderId)).thenReturn(AccountHolder.builder().id(accountHolderId).build());
        doNothing().when(accessControlService).assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.TRANSACT);
        when(seedCapitalCreditBridgeService.requireCandidate(accountHolderId, circleId))
                .thenThrow(new BusinessException("Credit application is waiting future gates: [IMF_CHANNEL_ACTIVE]"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> creditApplicationService.createSeedCapitalApplication(actorUserId, accountHolderId, request)
        );

        assertEquals("Credit application is waiting future gates: [IMF_CHANNEL_ACTIVE]", exception.getMessage());
        verify(creditApplicationRepository, never()).save(any(CreditApplication.class));
    }
}
