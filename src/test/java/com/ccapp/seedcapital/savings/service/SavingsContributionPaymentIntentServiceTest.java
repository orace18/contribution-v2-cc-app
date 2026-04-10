package com.ccapp.seedcapital.savings.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.service.KycStatusConsistencyService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.ccapp.seedcapital.savings.domain.entity.ImfSavingsChannel;
import com.ccapp.seedcapital.savings.domain.entity.MemberSavingsContribution;
import com.ccapp.seedcapital.savings.domain.entity.SavingsCycle;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsChannelStatus;
import com.ccapp.seedcapital.savings.domain.enums.MemberSavingsContributionStatus;
import com.ccapp.seedcapital.savings.domain.enums.SavingsContributionPaymentProvider;
import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;
import com.ccapp.seedcapital.savings.dto.CreateSavingsContributionPaymentIntentRequest;
import com.ccapp.seedcapital.savings.dto.SavingsContributionPaymentIntentResponse;
import com.ccapp.seedcapital.savings.repository.MemberSavingsContributionRepository;
import com.ccapp.wallet.service.WalletDebitFacade;
import com.ccapp.wallet.service.WalletGuardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingsContributionPaymentIntentServiceTest {

    @Mock
    private CircleReadinessAccessService circleReadinessAccessService;
    @Mock
    private SavingsCycleService savingsCycleService;
    @Mock
    private ImfSavingsChannelService imfSavingsChannelService;
    @Mock
    private MemberSavingsContributionRepository memberSavingsContributionRepository;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private WalletGuardService walletGuardService;
    @Mock
    private WalletDebitFacade walletDebitFacade;
    @Mock
    private KycStatusConsistencyService kycStatusConsistencyService;

    @InjectMocks
    private SavingsContributionPaymentIntentService savingsContributionPaymentIntentService;

    @Test
    void createMyPaymentIntent_shouldExecuteWalletDebitAndReturnPendingConfirmation() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        CreateSavingsContributionPaymentIntentRequest request = new CreateSavingsContributionPaymentIntentRequest(
                new BigDecimal("5000.0000"),
                "xof",
                SavingsContributionPaymentProvider.WALLET,
                "sav-cycle-2026-04-10-member-1",
                "12345",
                null,
                null
        );

        SavingsCycle cycle = SavingsCycle.builder()
                .id(cycleId)
                .circleId(circleId)
                .cycleNumber(2)
                .countryCode("BJ")
                .currency("XOF")
                .status(SavingsCycleStatus.OPEN)
                .expectedAmountPerMember(new BigDecimal("5000.0000"))
                .windowStart(OffsetDateTime.now().minusDays(1))
                .windowEnd(OffsetDateTime.now().plusDays(1))
                .participantCount(5)
                .build();
        MemberSavingsContribution contribution = MemberSavingsContribution.builder()
                .cycleId(cycleId)
                .accountHolderId(accountHolderId)
                .expectedAmount(new BigDecimal("5000.0000"))
                .confirmedAmount(BigDecimal.ZERO.setScale(4))
                .status(MemberSavingsContributionStatus.EXPECTED)
                .build();

        doNothing().when(circleReadinessAccessService).assertCircleMember(accountHolderId, circleId);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(accountHolderId)).thenReturn(KycStatus.VERIFIED);
        when(savingsCycleService.requireCycle(circleId, cycleId)).thenReturn(cycle);
        when(savingsCycleService.refreshCycleStatus(cycle)).thenReturn(cycle);
        when(imfSavingsChannelService.requireReadyChannel(accountHolderId, "BJ")).thenReturn(ImfSavingsChannel.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .partnerId(UUID.randomUUID())
                .countryCode("BJ")
                .status(ImfSavingsChannelStatus.ACTIVE)
                .build());
        when(memberSavingsContributionRepository.findByCycleIdAndAccountHolderId(cycleId, accountHolderId))
                .thenReturn(Optional.of(contribution));
        when(countryChartOfAccountsService.findOrCreate("BJ", com.ccapp.country.config.domain.enums.CountryAccountGroup.PARTNER_SETTLEMENT, "IMF savings settlement"))
                .thenReturn(CountryChartAccount.builder().accountCode("2400001").build());
        when(walletGuardService.requireActiveWallet(accountHolderId)).thenReturn(Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .status("ACTIVE")
                .build());
        when(walletDebitFacade.executeSecureDebit(any())).thenReturn(
                new WalletDebitFacade.WalletDebitExecutionResult(
                        true,
                        false,
                        operationId,
                        "CONFIRMED",
                        null,
                        null
                )
        );

        SavingsContributionPaymentIntentResponse response = savingsContributionPaymentIntentService.createMyPaymentIntent(
                actorUserId,
                accountHolderId,
                circleId,
                cycleId,
                request
        );

        assertEquals(operationId, response.intentId());
        assertEquals(operationId, response.operationId());
        assertEquals("PENDING_CONFIRMATION", response.status());

        ArgumentCaptor<WalletDebitFacade.SecureDebitRequest> captor = ArgumentCaptor.forClass(WalletDebitFacade.SecureDebitRequest.class);
        verify(walletDebitFacade).executeSecureDebit(captor.capture());
        WalletDebitFacade.SecureDebitRequest secureDebitRequest = captor.getValue();
        assertEquals("2400001", secureDebitRequest.destinationAccountCode());
        assertEquals("XOF", secureDebitRequest.currency());
        assertEquals("sav-cycle-2026-04-10-member-1", secureDebitRequest.idempotencyKey());
        assertEquals("IMF savings contribution circle=" + circleId + " cycle=" + cycleId, secureDebitRequest.description());
    }

    @Test
    void createMyPaymentIntent_shouldRejectAmountAboveOutstandingContribution() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        CreateSavingsContributionPaymentIntentRequest request = new CreateSavingsContributionPaymentIntentRequest(
                new BigDecimal("6000.0000"),
                "XOF",
                SavingsContributionPaymentProvider.WALLET,
                "sav-cycle-over-limit",
                "12345",
                null,
                null
        );

        SavingsCycle cycle = SavingsCycle.builder()
                .id(cycleId)
                .circleId(circleId)
                .countryCode("BJ")
                .currency("XOF")
                .status(SavingsCycleStatus.OPEN)
                .build();
        MemberSavingsContribution contribution = MemberSavingsContribution.builder()
                .cycleId(cycleId)
                .accountHolderId(accountHolderId)
                .expectedAmount(new BigDecimal("5000.0000"))
                .confirmedAmount(BigDecimal.ZERO.setScale(4))
                .status(MemberSavingsContributionStatus.EXPECTED)
                .build();

        doNothing().when(circleReadinessAccessService).assertCircleMember(accountHolderId, circleId);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(accountHolderId)).thenReturn(KycStatus.VERIFIED);
        when(savingsCycleService.requireCycle(circleId, cycleId)).thenReturn(cycle);
        when(savingsCycleService.refreshCycleStatus(cycle)).thenReturn(cycle);
        when(imfSavingsChannelService.requireReadyChannel(accountHolderId, "BJ")).thenReturn(ImfSavingsChannel.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .partnerId(UUID.randomUUID())
                .countryCode("BJ")
                .status(ImfSavingsChannelStatus.ACTIVE)
                .build());
        when(memberSavingsContributionRepository.findByCycleIdAndAccountHolderId(cycleId, accountHolderId))
                .thenReturn(Optional.of(contribution));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> savingsContributionPaymentIntentService.createMyPaymentIntent(
                        actorUserId,
                        accountHolderId,
                        circleId,
                        cycleId,
                        request
                )
        );

        assertEquals("Requested payment amount exceeds the outstanding savings contribution", exception.getMessage());
        verify(walletDebitFacade, never()).executeSecureDebit(any());
    }

    @Test
    void createMyPaymentIntent_shouldRejectWhenKycIsNotVerified() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = UUID.randomUUID();
        UUID circleId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        CreateSavingsContributionPaymentIntentRequest request = new CreateSavingsContributionPaymentIntentRequest(
                new BigDecimal("5000.0000"),
                "XOF",
                SavingsContributionPaymentProvider.WALLET,
                "sav-cycle-kyc-blocked",
                "12345",
                null,
                null
        );

        doNothing().when(circleReadinessAccessService).assertCircleMember(accountHolderId, circleId);
        when(kycStatusConsistencyService.reconcileAccountHolderStatus(accountHolderId)).thenReturn(KycStatus.PENDING);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> savingsContributionPaymentIntentService.createMyPaymentIntent(
                        actorUserId,
                        accountHolderId,
                        circleId,
                        cycleId,
                        request
                )
        );

        assertEquals("KYC_NOT_VERIFIED", exception.getMessage());
        verify(walletDebitFacade, never()).executeSecureDebit(any());
    }
}
