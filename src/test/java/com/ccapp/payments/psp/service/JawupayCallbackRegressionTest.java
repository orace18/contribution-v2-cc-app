package com.ccapp.payments.psp.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.service.PaymentService;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.payments.psp.domain.entity.PspCallbackEvent;
import com.ccapp.payments.psp.dto.JawupayCallbackRequest;
import com.ccapp.payments.psp.repository.PspCallbackEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JawupayCallbackRegressionTest {

    private static final String HMAC_SECRET = "super-secret-hmac-key";

    @Mock
    private OperationService operationService;
    @Mock
    private OperationRepository operationRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PspCallbackEventRepository callbackEventRepository;
    @Mock
    private SimpleRateLimiter simpleRateLimiter;
    @Mock
    private JawuPayWebhookService jawuPayWebhookService;

    @InjectMocks
    private JawupayService jawupayService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jawupayService, "jawupayHmacSecret", HMAC_SECRET);
        ReflectionTestUtils.setField(jawupayService, "replayWindowSeconds", 300L);
        ReflectionTestUtils.setField(jawupayService, "callbackRateLimitPerMinute", 60);
    }

    @Test
    void processCallback_shouldRejectInvalidSignature_andSkipStateChanges() {
        JawupayCallbackRequest request = callbackRequest("SUCCESS", Instant.now().getEpochSecond());
        when(simpleRateLimiter.allow("127.0.0.1", 60)).thenReturn(true);
        when(callbackEventRepository.existsByNonce("nonce-1")).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> jawupayService.processCallback(request, "bad-signature", "127.0.0.1")
        );

        verify(callbackEventRepository, never()).save(any(PspCallbackEvent.class));
        verify(operationService, never()).confirmByPspReference(any(), any(), any());
        verify(jawuPayWebhookService, never()).dispatchConfirmedSideEffects(any());
        verify(jawuPayWebhookService, never()).dispatchFailedSideEffects(any());
    }

    @Test
    void processCallback_shouldConfirmPaymentAndDelegateSideEffects_forSuccessfulStatus() throws Exception {
        JawupayCallbackRequest request = callbackRequest("SUCCESS", Instant.now().getEpochSecond());
        String signature = sign(request);
        UUID operationId = UUID.randomUUID();
        Operation confirmedOperation = Operation.builder().id(operationId).type(OperationType.SUBSCRIPTION).build();

        when(simpleRateLimiter.allow("10.0.0.1", 60)).thenReturn(true);
        when(callbackEventRepository.existsByNonce("nonce-1")).thenReturn(false);
        when(callbackEventRepository.save(any(PspCallbackEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(operationService.confirmByPspReference("PSP-REF-001", new BigDecimal("1500.00"), "XOF"))
                .thenReturn(confirmedOperation);

        jawupayService.processCallback(request, signature, "10.0.0.1");

        ArgumentCaptor<PspCallbackEvent> eventCaptor = ArgumentCaptor.forClass(PspCallbackEvent.class);
        verify(callbackEventRepository).save(eventCaptor.capture());
        assertEquals("nonce-1", eventCaptor.getValue().getNonce());
        assertEquals(signature, eventCaptor.getValue().getSignature());

        verify(operationService).confirmByPspReference("PSP-REF-001", new BigDecimal("1500.00"), "XOF");
        verify(jawuPayWebhookService).dispatchConfirmedSideEffects(confirmedOperation);
    }

    @Test
    void processCallback_shouldDelegateFailure_forNonSuccessfulStatus() throws Exception {
        JawupayCallbackRequest request = callbackRequest("FAILED", Instant.now().getEpochSecond());
        String signature = sign(request);
        UUID operationId = UUID.randomUUID();
        Operation failedOperation = Operation.builder()
                .id(operationId)
                .type(OperationType.SUBSCRIPTION)
                .build();

        when(simpleRateLimiter.allow("10.0.0.2", 60)).thenReturn(true);
        when(callbackEventRepository.existsByNonce("nonce-1")).thenReturn(false);
        when(callbackEventRepository.save(any(PspCallbackEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(operationService.failByPspReference("PSP-REF-001")).thenReturn(failedOperation);

        jawupayService.processCallback(request, signature, "10.0.0.2");

        verify(callbackEventRepository).save(any(PspCallbackEvent.class));
        verify(operationService).failByPspReference("PSP-REF-001");
        verify(jawuPayWebhookService).dispatchFailedSideEffects(failedOperation);
        verify(operationService, never()).confirmByPspReference(any(), any(), any());
        verify(jawuPayWebhookService, never()).dispatchConfirmedSideEffects(any());
    }

    private JawupayCallbackRequest callbackRequest(String status, long timestamp) {
        return new JawupayCallbackRequest(
                "PSP-REF-001",
                new BigDecimal("1500.00"),
                "XOF",
                status,
                "nonce-1",
                timestamp
        );
    }

    private String sign(JawupayCallbackRequest request) throws Exception {
        String payload = String.join("|",
                request.pspReference(),
                request.amount().toPlainString(),
                request.currency(),
                request.status(),
                request.nonce(),
                String.valueOf(request.timestamp()));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
