package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.service.JawuPayPaymentOrchestrationService;
import com.ccapp.wallet.dto.WalletDepositResponse;
import com.ccapp.wallet.dto.WalletPspDepositRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletDepositServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletGuardService walletGuardService;
    @Mock
    private WalletLimitService walletLimitService;
    @Mock
    private OperationService operationService;
    @Mock
    private AccountingPostingService accountingPostingService;
    @Mock
    private JawuPayPaymentOrchestrationService jawuPayPaymentOrchestrationService;
    @Mock
    private WalletDebitFacade walletDebitFacade;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private WalletDepositService walletDepositService;

    @Test
    void initiatePspDeposit_usesOrchestrationAndReturnsRedirectUrl() {
        UUID accountHolderId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .status("ACTIVE")
                .mobileMoneyNumber("22961000000")
                .provider("MTN")
                .build();

        AccountHolder holder = AccountHolder.builder()
                .id(accountHolderId)
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("22961000000")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .build();

        Operation initiated = Operation.builder()
                .id(operationId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .type(OperationType.WALLET_DEPOSIT_PSP)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("1500.00"))
                .currency("XOF")
                .idempotencyKey("idem-1")
                .description("PSP wallet deposit")
                .build();

        Operation pending = Operation.builder()
                .id(operationId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .type(OperationType.WALLET_DEPOSIT_PSP)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("1500.00"))
                .currency("XOF")
                .idempotencyKey("idem-1")
                .pspReference("payment-123")
                .redirectUrl("https://checkout.example/deposit/persisted-payment-123")
                .description("PSP wallet deposit")
                .build();

        WalletPspDepositRequest request = new WalletPspDepositRequest(
                new BigDecimal("1500.00"),
                "xof",
                "idem-1",
                "MOBILE_MONEY_MTN",
                null,
                null
        );

        when(walletGuardService.requireActiveWallet(accountHolderId)).thenReturn(wallet);
        when(accountHolderRepository.findById(accountHolderId)).thenReturn(Optional.of(holder));
        when(operationService.createInitiatedOperation(
                accountHolderId,
                actorUserId,
                OperationType.WALLET_DEPOSIT_PSP,
                new BigDecimal("1500.00"),
                "XOF",
                "idem-1",
                "PSP wallet deposit"
        )).thenReturn(initiated);
        when(jawuPayPaymentOrchestrationService.initiatePayment(
                accountHolderId,
                actorUserId,
                new BigDecimal("1500.00"),
                "XOF",
                "BJ",
                "MOBILE_MONEY_MTN",
                "22961000000",
                "MTN",
                "idem-1",
                "PSP wallet deposit"
        )).thenReturn(new JawuPayPaymentOrchestrationService.InitiatePaymentResult(
                operationId,
                "https://checkout.example/deposit/psp-response-payment-123"
        ));
        when(operationService.getById(operationId)).thenReturn(pending);

        WalletDepositResponse response = walletDepositService.initiatePspDeposit(accountHolderId, actorUserId, request);

        assertEquals("PENDING_PSP", response.status());
        assertEquals("payment-123", response.pspReference());
        assertEquals("https://checkout.example/deposit/persisted-payment-123", response.checkoutUrl());
        assertEquals(new BigDecimal("1500.00"), response.amount());
        assertEquals("XOF", response.currency());

        InOrder inOrder = inOrder(operationService, jawuPayPaymentOrchestrationService);
        inOrder.verify(operationService).createInitiatedOperation(
                accountHolderId,
                actorUserId,
                OperationType.WALLET_DEPOSIT_PSP,
                new BigDecimal("1500.00"),
                "XOF",
                "idem-1",
                "PSP wallet deposit"
        );
        inOrder.verify(jawuPayPaymentOrchestrationService).initiatePayment(
                accountHolderId,
                actorUserId,
                new BigDecimal("1500.00"),
                "XOF",
                "BJ",
                "MOBILE_MONEY_MTN",
                "22961000000",
                "MTN",
                "idem-1",
                "PSP wallet deposit"
        );
    }

    @Test
    void initiatePspDeposit_existingPendingOperation_skipsOrchestration() {
        UUID accountHolderId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .status("ACTIVE")
                .build();

        Operation pending = Operation.builder()
                .id(operationId)
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .type(OperationType.WALLET_DEPOSIT_PSP)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("1500.00"))
                .currency("XOF")
                .idempotencyKey("idem-1")
                .pspReference("payment-123")
                .redirectUrl("https://checkout.example/deposit/existing-payment-123")
                .description("PSP wallet deposit")
                .build();

        WalletPspDepositRequest request = new WalletPspDepositRequest(
                new BigDecimal("1500.00"),
                "XOF",
                "idem-1",
                "MOBILE_MONEY_MTN",
                "22961000000",
                "MTN"
        );

        when(walletGuardService.requireActiveWallet(accountHolderId)).thenReturn(wallet);
        when(operationService.createInitiatedOperation(
                accountHolderId,
                actorUserId,
                OperationType.WALLET_DEPOSIT_PSP,
                new BigDecimal("1500.00"),
                "XOF",
                "idem-1",
                "PSP wallet deposit"
        )).thenReturn(pending);

        WalletDepositResponse response = walletDepositService.initiatePspDeposit(accountHolderId, actorUserId, request);

        assertEquals("PENDING_PSP", response.status());
        assertEquals("payment-123", response.pspReference());
        assertEquals("https://checkout.example/deposit/existing-payment-123", response.checkoutUrl());
        verify(jawuPayPaymentOrchestrationService, never()).initiatePayment(
                eq(accountHolderId),
                eq(actorUserId),
                eq(new BigDecimal("1500.00")),
                eq("XOF"),
                eq("BJ"),
                eq("MOBILE_MONEY_MTN"),
                eq("22961000000"),
                eq("MTN"),
                eq("idem-1"),
                eq("PSP wallet deposit")
        );
    }
}
