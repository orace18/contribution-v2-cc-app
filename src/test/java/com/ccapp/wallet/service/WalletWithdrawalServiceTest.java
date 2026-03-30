package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.service.JawuPayPayoutOrchestrationService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletWithdrawalServiceTest {

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
    private WalletWithdrawalService walletWithdrawalService;

    @Test
    void withdraw_reservesBeforePayoutAndReturnsPending() {
        UUID accountHolderId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();

        WalletWithdrawRequest request = new WalletWithdrawRequest(
                new BigDecimal("1500.00"),
                "XOF",
                "withdraw-idem-1",
                "1234",
                null,
                null
        );

        AccountHolder holder = AccountHolder.builder()
                .id(accountHolderId)
                .kycStatus(KycStatus.VERIFIED)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .phoneNumber("22961000000")
                .build();
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .status("ACTIVE")
                .mobileMoneyNumber("22961000000")
                .provider("MTN")
                .build();

        CountryChartAccount pspClearing = CountryChartAccount.builder()
                .accountCode("PSP_CLEARING_BJ")
                .build();

        Operation initiated = Operation.builder()
                .id(operationId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .type(OperationType.WALLET_WITHDRAWAL)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("1500.00"))
                .currency("XOF")
                .idempotencyKey("withdraw-idem-1")
                .description("Wallet withdrawal - payout via JawuPay")
                .build();
        Operation pending = Operation.builder()
                .id(operationId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .type(OperationType.WALLET_WITHDRAWAL)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("1500.00"))
                .currency("XOF")
                .pspReference(payoutId.toString())
                .idempotencyKey("withdraw-idem-1")
                .description("Wallet withdrawal - payout via JawuPay")
                .build();

        when(accountHolderRepository.findById(accountHolderId)).thenReturn(Optional.of(holder));
        when(walletGuardService.requireActiveWallet(accountHolderId)).thenReturn(wallet);
        when(countryChartOfAccountsService.resolveSystemAccount(any(), any())).thenReturn(pspClearing);
        when(walletDebitFacade.executeSecureDebit(any())).thenReturn(
                new WalletDebitFacade.WalletDebitExecutionResult(
                        true, false, operationId, "INITIATED", null, null
                )
        );
        when(operationService.getById(operationId)).thenReturn(initiated, pending);
        when(jawuPayPayoutOrchestrationService.createPayout(
                eq(accountHolderId),
                eq(actorUserId),
                eq(new BigDecimal("1500.00")),
                eq("XOF"),
                eq("BJ"),
                eq(null),
                eq("22961000000"),
                eq("MTN"),
                eq("withdraw-idem-1"),
                eq("Wallet withdrawal - payout via JawuPay")
        )).thenReturn(new JawuPayPayoutOrchestrationService.InitiatePayoutResult(operationId, payoutId));

        WalletWithdrawResponse response = walletWithdrawalService.withdraw(accountHolderId, actorUserId, request);

        assertEquals("PENDING_PSP", response.status());
        assertEquals(operationId, response.operationId());
        assertEquals(payoutId.toString(), response.pspReference());

        ArgumentCaptor<com.ccapp.wallet.dto.WalletReserveRequest> reservationCaptor =
                ArgumentCaptor.forClass(com.ccapp.wallet.dto.WalletReserveRequest.class);
        InOrder inOrder = inOrder(walletReservationService, jawuPayPayoutOrchestrationService);
        inOrder.verify(walletReservationService).reserveSystem(eq(accountHolderId), reservationCaptor.capture(), eq(operationId));
        inOrder.verify(jawuPayPayoutOrchestrationService).createPayout(
                eq(accountHolderId),
                eq(actorUserId),
                eq(new BigDecimal("1500.00")),
                eq("XOF"),
                eq("BJ"),
                eq(null),
                eq("22961000000"),
                eq("MTN"),
                eq("withdraw-idem-1"),
                eq("Wallet withdrawal - payout via JawuPay")
        );
        assertEquals(new BigDecimal("1500.00"), reservationCaptor.getValue().amount());
        assertEquals("XOF", reservationCaptor.getValue().currency());
    }

    @Test
    void withdraw_pspSubmissionFailure_releasesReservationAndFailsOperation() {
        UUID accountHolderId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        WalletWithdrawRequest request = new WalletWithdrawRequest(
                new BigDecimal("1500.00"),
                "XOF",
                "withdraw-idem-2",
                "1234",
                null,
                null
        );

        AccountHolder holder = AccountHolder.builder()
                .id(accountHolderId)
                .kycStatus(KycStatus.VERIFIED)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .phoneNumber("22961000000")
                .build();
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .status("ACTIVE")
                .mobileMoneyNumber("22961000000")
                .provider("MTN")
                .build();
        CountryChartAccount pspClearing = CountryChartAccount.builder()
                .accountCode("PSP_CLEARING_BJ")
                .build();
        Operation initiated = Operation.builder()
                .id(operationId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .type(OperationType.WALLET_WITHDRAWAL)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("1500.00"))
                .currency("XOF")
                .idempotencyKey("withdraw-idem-2")
                .description("Wallet withdrawal - payout via JawuPay")
                .build();

        when(accountHolderRepository.findById(accountHolderId)).thenReturn(Optional.of(holder));
        when(walletGuardService.requireActiveWallet(accountHolderId)).thenReturn(wallet);
        when(countryChartOfAccountsService.resolveSystemAccount(any(), any())).thenReturn(pspClearing);
        when(walletDebitFacade.executeSecureDebit(any())).thenReturn(
                new WalletDebitFacade.WalletDebitExecutionResult(
                        true, false, operationId, "INITIATED", null, null
                )
        );
        when(operationService.getById(operationId)).thenReturn(initiated);
        when(jawuPayPayoutOrchestrationService.createPayout(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("psp unavailable"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> walletWithdrawalService.withdraw(accountHolderId, actorUserId, request));
        assertEquals("psp unavailable", thrown.getMessage());

        verify(walletReservationService).releaseReservation(operationId);
        verify(operationService).markFailed(operationId);
    }

    @Test
    void confirmWithdrawal_confirmsPostsAndReleasesReservation() {
        UUID accountHolderId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        Operation pending = Operation.builder()
                .id(operationId)
                .accountHolderId(accountHolderId)
                .type(OperationType.WALLET_WITHDRAWAL)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("750.00"))
                .currency("XOF")
                .build();
        Operation confirmed = Operation.builder()
                .id(operationId)
                .accountHolderId(accountHolderId)
                .type(OperationType.WALLET_WITHDRAWAL)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("750.00"))
                .currency("XOF")
                .build();
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .status("ACTIVE")
                .build();
        AccountHolder holder = AccountHolder.builder()
                .id(accountHolderId)
                .kycStatus(KycStatus.VERIFIED)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .build();
        CountryChartAccount walletAccount = CountryChartAccount.builder()
                .accountCode("WALLET_BJ_001")
                .build();

        when(operationService.getById(operationId)).thenReturn(pending);
        when(operationService.confirmOperation(operationId)).thenReturn(confirmed);
        when(walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId)).thenReturn(List.of(wallet));
        when(accountHolderRepository.findById(accountHolderId)).thenReturn(Optional.of(holder));
        when(countryChartOfAccountsService.findOrCreateWallet(holder)).thenReturn(walletAccount);

        walletWithdrawalService.confirmWithdrawal(operationId);

        verify(accountingPostingService).postWalletWithdrawal(confirmed, "WALLET_BJ_001");
        verify(walletReservationService).releaseReservation(operationId);
        verify(eventPublisher).publish(any());
    }
}
