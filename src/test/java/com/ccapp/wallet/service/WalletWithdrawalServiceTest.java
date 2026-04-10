package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.enums.KycLevel;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.service.JawuPayPayoutOrchestrationService;
import com.ccapp.wallet.dto.WalletReserveRequest;
import com.ccapp.wallet.dto.WalletReserveResponse;
import com.ccapp.wallet.dto.WalletWithdrawRequest;
import com.ccapp.wallet.dto.WalletWithdrawResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletWithdrawalServiceTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.randomUUID();
    private static final UUID ACTOR_USER_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID PAYOUT_ID = UUID.randomUUID();

    @Mock
    private WalletGuardService walletGuardService;
    @Mock
    private WalletDebitFacade walletDebitFacade;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private OperationService operationService;
    @Mock
    private JawuPayPayoutOrchestrationService jawuPayPayoutOrchestrationService;
    @Mock
    private WalletReservationService walletReservationService;
    @Mock
    private AccountingPostingService accountingPostingService;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private WalletWithdrawalService service;

    @Test
    void withdraw_authorizedCreatesReservationBeforeSubmittingPayout() {
        AccountHolder holder = verifiedHolder();
        Wallet wallet = activeWallet();
        CountryChartAccount pspClearing = CountryChartAccount.builder()
                .accountCode("PSP-CLEARING")
                .build();
        Operation initiated = withdrawalOperation(OperationStatus.INITIATED);
        Operation pending = withdrawalOperation(OperationStatus.PENDING_PSP);
        pending.setPayoutId(PAYOUT_ID.toString());
        pending.setPspReference(PAYOUT_ID.toString());

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(holder));
        when(walletGuardService.requireActiveWallet(ACCOUNT_HOLDER_ID)).thenReturn(wallet);
        when(countryChartOfAccountsService.resolveSystemAccount("CI", CountryAccountSystemTag.PSP_CLEARING))
                .thenReturn(pspClearing);
        when(walletDebitFacade.executeSecureDebit(any()))
                .thenReturn(WalletDebitFacade.WalletDebitExecutionResult.authorized(initiated));
        when(operationService.getById(OPERATION_ID)).thenReturn(initiated, pending);
        when(walletReservationService.reserveSystem(eq(ACCOUNT_HOLDER_ID), any(), eq(OPERATION_ID)))
                .thenReturn(new WalletReserveResponse(UUID.randomUUID(), WALLET_ID, initiated.getAmount(), "ACTIVE", null));
        when(jawuPayPayoutOrchestrationService.createPayout(
                OPERATION_ID,
                "CI",
                "MOBILE_MONEY_MTN",
                "22501234567",
                "MTN"
        )).thenReturn(new JawuPayPayoutOrchestrationService.InitiatePayoutResult(OPERATION_ID, PAYOUT_ID));

        WalletWithdrawResponse response = service.withdraw(
                ACCOUNT_HOLDER_ID,
                ACTOR_USER_ID,
                new WalletWithdrawRequest(new BigDecimal("2500.00"), "xof", "idem-withdraw", "1234", null, null)
        );

        assertEquals(OPERATION_ID, response.operationId());
        assertEquals("PENDING_PSP", response.status());
        assertEquals(PAYOUT_ID.toString(), response.pspReference());

        ArgumentCaptor<WalletReserveRequest> reservationCaptor = ArgumentCaptor.forClass(WalletReserveRequest.class);
        InOrder inOrder = inOrder(walletReservationService, jawuPayPayoutOrchestrationService);
        inOrder.verify(walletReservationService).reserveSystem(eq(ACCOUNT_HOLDER_ID), reservationCaptor.capture(), eq(OPERATION_ID));
        inOrder.verify(jawuPayPayoutOrchestrationService).createPayout(
                OPERATION_ID,
                "CI",
                "MOBILE_MONEY_MTN",
                "22501234567",
                "MTN"
        );

        WalletReserveRequest reservationRequest = reservationCaptor.getValue();
        assertThat(reservationRequest.amount()).isEqualByComparingTo("2500.00");
        assertThat(reservationRequest.currency()).isEqualTo("XOF");
        assertThat(reservationRequest.reason()).isEqualTo("wallet-withdrawal-hold:" + OPERATION_ID);
        assertThat(reservationRequest.expiryDate()).isNotNull();
    }

    @Test
    void withdraw_payoutSubmissionFailureReleasesReservationAndFailsOperation() {
        AccountHolder holder = verifiedHolder();
        Wallet wallet = activeWallet();
        CountryChartAccount pspClearing = CountryChartAccount.builder()
                .accountCode("PSP-CLEARING")
                .build();
        Operation initiated = withdrawalOperation(OperationStatus.INITIATED);

        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(holder));
        when(walletGuardService.requireActiveWallet(ACCOUNT_HOLDER_ID)).thenReturn(wallet);
        when(countryChartOfAccountsService.resolveSystemAccount("CI", CountryAccountSystemTag.PSP_CLEARING))
                .thenReturn(pspClearing);
        when(walletDebitFacade.executeSecureDebit(any()))
                .thenReturn(WalletDebitFacade.WalletDebitExecutionResult.authorized(initiated));
        when(operationService.getById(OPERATION_ID)).thenReturn(initiated, initiated);
        when(walletReservationService.reserveSystem(eq(ACCOUNT_HOLDER_ID), any(), eq(OPERATION_ID)))
                .thenReturn(new WalletReserveResponse(UUID.randomUUID(), WALLET_ID, initiated.getAmount(), "ACTIVE", null));
        when(jawuPayPayoutOrchestrationService.createPayout(
                OPERATION_ID,
                "CI",
                "MOBILE_MONEY_MTN",
                "22501234567",
                "MTN"
        )).thenThrow(new IllegalStateException("PSP unavailable"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.withdraw(
                ACCOUNT_HOLDER_ID,
                ACTOR_USER_ID,
                new WalletWithdrawRequest(new BigDecimal("2500.00"), "XOF", "idem-withdraw", "1234", null, null)
        ));

        assertEquals("PSP unavailable", thrown.getMessage());
        verify(walletReservationService).releaseReservation(OPERATION_ID);
        verify(operationService).markFailed(OPERATION_ID);
    }

    @Test
    void confirmWithdrawal_consumesReservationAndPostsLedger() {
        Operation pending = withdrawalOperation(OperationStatus.PENDING_PSP);
        Operation confirmed = withdrawalOperation(OperationStatus.CONFIRMED);
        AccountHolder holder = verifiedHolder();
        Wallet wallet = activeWallet();
        CountryChartAccount walletAccount = CountryChartAccount.builder()
                .accountCode("WALLET-ACCOUNT")
                .build();

        when(operationService.getById(OPERATION_ID)).thenReturn(pending);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);
        when(walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(ACCOUNT_HOLDER_ID)).thenReturn(List.of(wallet));
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(holder));
        when(countryChartOfAccountsService.findOrCreateWallet(holder)).thenReturn(walletAccount);

        service.confirmWithdrawal(OPERATION_ID);

        verify(walletReservationService).consumeReservation(OPERATION_ID);
        verify(accountingPostingService).postWalletWithdrawal(confirmed, "WALLET-ACCOUNT");
        verify(eventPublisher).publish(any());
    }

    @Test
    void handlePspWithdrawalFailure_releasesReservationAndMarksFailed() {
        Operation pending = withdrawalOperation(OperationStatus.PENDING_PSP);
        Operation failed = withdrawalOperation(OperationStatus.FAILED);
        when(operationService.getById(OPERATION_ID)).thenReturn(pending);
        when(operationService.markFailed(OPERATION_ID)).thenReturn(failed);

        service.handlePspWithdrawalFailure(OPERATION_ID);

        verify(operationService).markFailed(OPERATION_ID);
        verify(walletReservationService).releaseReservation(OPERATION_ID);
        verify(accountingPostingService, never()).postWalletWithdrawal(any(), any());
    }

    @Test
    void withdraw_rejectsVerifiedStatusWithoutBasicKycLevel() {
        AccountHolder holder = AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .phoneNumber("22501234567")
                .countryCode("CI")
                .serviceCountryCode("CI")
                .phoneVerifiedAt(OffsetDateTime.now())
                .kycStatus(KycStatus.VERIFIED)
                .kycLevel(KycLevel.LEVEL_0_UNVERIFIED)
                .build();
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(holder));

        BusinessException thrown = assertThrows(BusinessException.class, () -> service.withdraw(
                ACCOUNT_HOLDER_ID,
                ACTOR_USER_ID,
                new WalletWithdrawRequest(new BigDecimal("2500.00"), "XOF", "idem-withdraw", "1234", null, null)
        ));

        assertThat(thrown.getMessage()).contains("level: LEVEL_0_UNVERIFIED");
        verify(walletGuardService, never()).requireActiveWallet(any());
    }

    @Test
    void withdraw_rejectsWhenPhoneIsNotVerified() {
        AccountHolder holder = AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .phoneNumber("22501234567")
                .countryCode("CI")
                .serviceCountryCode("CI")
                .kycStatus(KycStatus.VERIFIED)
                .kycLevel(KycLevel.LEVEL_1_BASIC)
                .build();
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(holder));

        BusinessException thrown = assertThrows(BusinessException.class, () -> service.withdraw(
                ACCOUNT_HOLDER_ID,
                ACTOR_USER_ID,
                new WalletWithdrawRequest(new BigDecimal("2500.00"), "XOF", "idem-withdraw", "1234", null, null)
        ));

        assertThat(thrown.getMessage()).isEqualTo("Phone verification is required for withdrawal");
        verify(walletGuardService, never()).requireActiveWallet(any());
    }

    private AccountHolder verifiedHolder() {
        return AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .phoneNumber("22501234567")
                .countryCode("CI")
                .serviceCountryCode("CI")
                .phoneVerifiedAt(OffsetDateTime.now())
                .kycStatus(KycStatus.VERIFIED)
                .kycLevel(KycLevel.LEVEL_1_BASIC)
                .build();
    }

    private Wallet activeWallet() {
        return Wallet.builder()
                .id(WALLET_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .provider("MTN")
                .mobileMoneyNumber("22501234567")
                .status("ACTIVE")
                .build();
    }

    private Operation withdrawalOperation(OperationStatus status) {
        return Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .actorUserId(ACTOR_USER_ID)
                .type(OperationType.WALLET_WITHDRAWAL)
                .status(status)
                .amount(new BigDecimal("2500.00"))
                .currency("XOF")
                .idempotencyKey("idem-withdraw")
                .build();
    }
}
