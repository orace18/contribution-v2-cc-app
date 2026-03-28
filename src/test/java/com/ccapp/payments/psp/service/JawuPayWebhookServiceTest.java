package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.client.JawuPayPspClient;
import com.ccapp.payments.psp.dto.JawuPayPaymentResponse;
import com.ccapp.payments.psp.dto.JawuPayWebhookRequest;
import com.ccapp.wallet.service.WalletDepositService;
import com.ccapp.wallet.service.WalletWithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JawuPayWebhookServiceTest {

    @Mock OperationService operationService;
    @Mock OperationRepository operationRepository;
    @Mock JawuPayPspClient pspClient;
    @Mock AccountingPostingService accountingPostingService;
    @Mock WalletDepositService walletDepositService;
    @Mock WalletWithdrawalService walletWithdrawalService;

    @InjectMocks
    JawuPayWebhookService service;

    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final String PAYMENT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "allowedIpsConfig", "");
        service.init();
    }

    // -------------------------------------------------------------------------
    // IP allowlist
    // -------------------------------------------------------------------------

    @Test
    void processWebhook_ipNotInAllowlist_throwsBusinessException() {
        ReflectionTestUtils.setField(service, "allowedIpsConfig", "10.0.0.1,10.0.0.2");
        service.init();

        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null);

        assertThatThrownBy(() -> service.processWebhook(request, "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unauthorized IP");
    }

    @Test
    void processWebhook_allowlistEmpty_permitsAnyIp() {
        when(operationRepository.findByPspReference(PAYMENT_ID))
                .thenReturn(Optional.empty());

        JawuPayWebhookRequest request = new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null);
        // Should not throw even from an unexpected IP when allowlist is empty
        service.processWebhook(request, "9.9.9.9");
    }

    // -------------------------------------------------------------------------
    // processWebhook — status routing
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "CONFIRMED", "PAID", "COMPLETED"})
    void processWebhook_successStatuses_confirmAndDispatchToDepositService(String rawStatus) {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationRepository.findByPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", rawStatus, PAYMENT_ID, null), "127.0.0.1");

        verify(operationService).confirmOperation(OPERATION_ID);
        verify(walletDepositService).confirmPspDeposit(OPERATION_ID);
        verifyNoInteractions(walletWithdrawalService, accountingPostingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "DECLINED", "REJECTED", "CANCELLED", "CANCELED"})
    void processWebhook_failureStatuses_markFailed(String rawStatus) {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationRepository.findByPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", rawStatus, PAYMENT_ID, null), "127.0.0.1");

        verify(operationService).markFailed(OPERATION_ID);
        verifyNoInteractions(walletDepositService, accountingPostingService);
    }

    @Test
    void processWebhook_pendingStatus_noStateChange() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationRepository.findByPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", "PENDING", PAYMENT_ID, null), "127.0.0.1");

        verify(operationService, never()).confirmOperation(any());
        verify(operationService, never()).markFailed(any());
    }

    @Test
    void processWebhook_alreadyConfirmed_idempotentNoAction() {
        Operation op = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationRepository.findByPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null), "127.0.0.1");

        verify(operationService, never()).confirmOperation(any());
    }

    @Test
    void processWebhook_unknownPspReference_logsAndReturns() {
        when(operationRepository.findByPspReference(PAYMENT_ID)).thenReturn(Optional.empty());

        service.processWebhook(new JawuPayWebhookRequest("JAWUPAY", "SUCCESS", PAYMENT_ID, null), "127.0.0.1");

        verifyNoInteractions(operationService);
    }

    // -------------------------------------------------------------------------
    // checkAndApplyPaymentStatus — fallback
    // -------------------------------------------------------------------------

    @Test
    void checkAndApplyPaymentStatus_amountMismatch_throwsBusinessException() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        // operation amount: 1500.00, psp returns 200000 minor units = 2000.00
        when(operationRepository.findByPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        JawuPayPaymentResponse payment = new JawuPayPaymentResponse(
                UUID.fromString(PAYMENT_ID), null, "SUCCESS",
                200000L, "XOF", "CI", "MOBILE_MONEY_MTN", "PSP", null,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(pspClient.getPayment(PAYMENT_ID)).thenReturn(payment);

        assertThatThrownBy(() -> service.checkAndApplyPaymentStatus(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("amount mismatch");
    }

    @Test
    void checkAndApplyPaymentStatus_matchingAmount_appliesStatus() {
        Operation op = pendingPspOperation(OperationType.WALLET_DEPOSIT_PSP);
        // operation amount: 1500.00 = 150000 minor units
        when(operationRepository.findByPspReference(PAYMENT_ID)).thenReturn(Optional.of(op));

        JawuPayPaymentResponse payment = new JawuPayPaymentResponse(
                UUID.fromString(PAYMENT_ID), null, "SUCCESS",
                150000L, "XOF", "CI", "MOBILE_MONEY_MTN", "PSP", null,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(pspClient.getPayment(PAYMENT_ID)).thenReturn(payment);

        Operation confirmed = confirmedOperation(OperationType.WALLET_DEPOSIT_PSP);
        when(operationService.confirmOperation(OPERATION_ID)).thenReturn(confirmed);

        service.checkAndApplyPaymentStatus(PAYMENT_ID);

        verify(operationService).confirmOperation(OPERATION_ID);
        verify(walletDepositService).confirmPspDeposit(OPERATION_ID);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Operation pendingPspOperation(OperationType type) {
        Operation op = new Operation();
        op.setId(OPERATION_ID);
        op.setStatus(OperationStatus.PENDING_PSP);
        op.setType(type);
        op.setAmount(new BigDecimal("1500.00"));
        op.setCurrency("XOF");
        return op;
    }

    private Operation confirmedOperation(OperationType type) {
        Operation op = new Operation();
        op.setId(OPERATION_ID);
        op.setStatus(OperationStatus.CONFIRMED);
        op.setType(type);
        op.setAmount(new BigDecimal("1500.00"));
        op.setCurrency("XOF");
        return op;
    }
}
