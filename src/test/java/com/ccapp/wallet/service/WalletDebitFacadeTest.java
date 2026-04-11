package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletDebitFacadeTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.fromString("80cbd72d-bb88-481b-aee0-4909d4e46b93");
    private static final UUID ACTOR_USER_ID = UUID.fromString("d5125b18-4ea4-4c9b-9b83-06ff1b5b5ae9");
    private static final UUID OPERATION_ID = UUID.fromString("960272e8-58ae-4b82-b3b2-eda8edb48341");
    private static final UUID WALLET_ID = UUID.fromString("0f6ce87a-79b3-4580-9fd7-783e48352a3f");

    @Mock
    private WalletGuardService walletGuardService;
    @Mock
    private WalletSecurityService walletSecurityService;
    @Mock
    private OperationService operationService;
    @Mock
    private AccountingPostingService accountingPostingService;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private WalletDebitFacade walletDebitFacade;

    @Test
    void executeSecureDebit_confirmOnly_confirmsWithoutPosting() {
        Wallet wallet = wallet();
        Operation initiated = operation(OperationStatus.INITIATED);
        Operation confirmed = operation(OperationStatus.CONFIRMED);
        WalletDebitFacade.SecureDebitRequest request = request(
                wallet,
                WalletDebitFacade.SecureDebitPostingMode.CONFIRM_ONLY
        );

        when(operationService.findByIdempotencyKey("membership:test")).thenReturn(Optional.empty());
        when(operationService.createInitiatedOperation(
                ACCOUNT_HOLDER_ID,
                ACTOR_USER_ID,
                OperationType.SUBSCRIPTION,
                new BigDecimal("19000.00"),
                "XOF",
                "membership:test",
                "Subscription wallet debit",
                null,
                new OperationService.OperationRouting(null, null, null, "REV-SUB")
        )).thenReturn(initiated);
        when(walletSecurityService.authorizeDebitIntent(
                eq(wallet),
                eq(ACCOUNT_HOLDER_ID),
                eq(initiated),
                eq("12345"),
                isNull(),
                isNull(),
                eq("fingerprint-1")
        )).thenReturn(WalletSecurityService.WalletAuthorizationResult.approvedResult());
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        WalletDebitFacade.WalletDebitExecutionResult result = walletDebitFacade.executeSecureDebit(request);

        assertTrue(result.approved());
        assertFalse(result.otpRequired());
        assertEquals(OPERATION_ID, result.operationId());
        assertEquals(OperationStatus.CONFIRMED.name(), result.status());
        verify(operationService).confirmOperation(OPERATION_ID);
        verify(accountingPostingService, never()).postConfirmedOperation(any());
        verify(accountingPostingService, never()).postSecureWalletDebit(any(), any(), any(), any(), any());
        verify(eventPublisher).publish(any());
    }

    @Test
    void executeSecureDebit_none_authorizesWithoutConfirming() {
        Wallet wallet = wallet();
        Operation initiated = operation(OperationStatus.INITIATED);
        WalletDebitFacade.SecureDebitRequest request = request(
                wallet,
                WalletDebitFacade.SecureDebitPostingMode.NONE
        );

        when(operationService.findByIdempotencyKey("membership:test")).thenReturn(Optional.empty());
        when(operationService.createInitiatedOperation(
                ACCOUNT_HOLDER_ID,
                ACTOR_USER_ID,
                OperationType.SUBSCRIPTION,
                new BigDecimal("19000.00"),
                "XOF",
                "membership:test",
                "Subscription wallet debit",
                null,
                new OperationService.OperationRouting(null, null, null, "REV-SUB")
        )).thenReturn(initiated);
        when(walletSecurityService.authorizeDebitIntent(
                eq(wallet),
                eq(ACCOUNT_HOLDER_ID),
                eq(initiated),
                eq("12345"),
                isNull(),
                isNull(),
                eq("fingerprint-1")
        )).thenReturn(WalletSecurityService.WalletAuthorizationResult.approvedResult());

        WalletDebitFacade.WalletDebitExecutionResult result = walletDebitFacade.executeSecureDebit(request);

        assertTrue(result.approved());
        assertFalse(result.otpRequired());
        assertEquals(OPERATION_ID, result.operationId());
        assertEquals(OperationStatus.INITIATED.name(), result.status());
        verify(operationService, never()).confirmOperation(any());
        verify(accountingPostingService, never()).postConfirmedOperation(any());
        verify(eventPublisher, never()).publish(any());
    }

    private Wallet wallet() {
        return Wallet.builder()
                .id(WALLET_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .build();
    }

    private Operation operation(OperationStatus status) {
        return Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .actorUserId(ACTOR_USER_ID)
                .type(OperationType.SUBSCRIPTION)
                .status(status)
                .amount(new BigDecimal("19000.00"))
                .currency("XOF")
                .idempotencyKey("membership:test")
                .destinationAccountCode("REV-SUB")
                .description("Subscription wallet debit")
                .build();
    }

    private WalletDebitFacade.SecureDebitRequest request(
            Wallet wallet,
            WalletDebitFacade.SecureDebitPostingMode postingMode
    ) {
        return new WalletDebitFacade.SecureDebitRequest(
                ACCOUNT_HOLDER_ID,
                ACCOUNT_HOLDER_ID,
                ACTOR_USER_ID,
                wallet,
                OperationType.SUBSCRIPTION,
                new BigDecimal("19000.00"),
                "XOF",
                "membership:test",
                "Subscription wallet debit",
                "fingerprint-1",
                "REV-SUB",
                null,
                null,
                null,
                "12345",
                null,
                null,
                false,
                false,
                "Wallet debit",
                "Subscription revenue",
                null,
                null,
                postingMode,
                "BJ"
        );
    }
}
