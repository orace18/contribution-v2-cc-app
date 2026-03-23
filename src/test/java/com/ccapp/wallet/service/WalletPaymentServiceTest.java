package com.ccapp.wallet.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.wallet.dto.WalletPayRequest;
import com.ccapp.wallet.dto.WalletPayResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletPaymentServiceTest {

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
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private WalletSecurityService walletSecurityService;
    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private WalletPaymentService walletPaymentService;

    @Test
    void payShouldReturnOtpRequiredWithoutPostingLedger() {
        UUID senderId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();

        Wallet senderWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(senderId)
                .status("ACTIVE")
                .mobileMoneyNumber("22961000000")
                .provider("MTN")
                .pinFailedAttempts(0)
                .build();
        Wallet destinationWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(destinationId)
                .status("ACTIVE")
                .mobileMoneyNumber("22962000000")
                .provider("MTN")
                .pinFailedAttempts(0)
                .build();
        Operation operation = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(senderId)
                .actorUserId(UUID.randomUUID())
                .type(OperationType.WALLET_PAYMENT)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("15000"))
                .currency("XOF")
                .idempotencyKey("idem-1")
                .build();

        WalletPayRequest request = new WalletPayRequest(
                "22962000000",
                new BigDecimal("15000"),
                "XOF",
                "payment",
                "idem-1",
                "1234",
                null,
                null
        );

        when(walletGuardService.requireActiveWallet(senderId)).thenReturn(senderWallet);
        when(walletRepository.findByMobileMoneyNumber("22962000000")).thenReturn(Optional.of(destinationWallet));
        when(operationService.createInitiatedOperation(
                senderId,
                operation.getActorUserId(),
                OperationType.WALLET_PAYMENT,
                new BigDecimal("15000"),
                "XOF",
                "idem-1",
                "payment"
        )).thenReturn(operation);
        when(walletSecurityService.authorizeDebit(
                senderWallet,
                senderId,
                operation,
                "1234",
                null,
                null,
                "wallet-payment|15000|XOF|22962000000"
        )).thenReturn(new WalletSecurityService.WalletAuthorizationResult(
                false,
                true,
                UUID.randomUUID(),
                OffsetDateTime.now().plusMinutes(2)
        ));

        WalletPayResponse response = walletPaymentService.pay(senderId, operation.getActorUserId(), request);

        assertTrue(response.otpRequired());
        assertEquals("OTP_REQUIRED", response.status());
        verify(operationService, never()).confirmOperation(any());
        verify(accountingPostingService, never()).postWalletPayment(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }
}
