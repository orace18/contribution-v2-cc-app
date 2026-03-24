package com.ccapp.payments.service;

import com.ccapp.payments.psp.client.JawupayClient;
import com.ccapp.payments.psp.dto.JawupayInitiateRequest;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
import com.ccapp.payments.psp.dto.JawupayPaymentStatusResponse;
import com.ccapp.payments.psp.dto.JawupayPayoutRequest;
import com.ccapp.payments.psp.dto.JawupayPayoutResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String PSP_PROVIDER = "JAWUPAY";

    private final JawupayClient jawupayClient;

    public JawupayInitiateResponse initiatePayment(UUID operationId,
                                                   String idempotencyKey,
                                                   BigDecimal amount,
                                                   String currency,
                                                   UUID accountHolderId,
                                                   UUID actorUserId,
                                                   String description) {
        return jawupayClient.initiatePayment(new JawupayInitiateRequest(
                operationId,
                idempotencyKey,
                amount,
                currency,
                accountHolderId,
                actorUserId,
                description
        ));
    }

    public String provider() {
        return PSP_PROVIDER;
    }

    public JawupayPayoutResponse initiatePayout(UUID operationId,
                                                String idempotencyKey,
                                                BigDecimal amount,
                                                String currency,
                                                UUID accountHolderId,
                                                UUID actorUserId,
                                                String destinationPhone,
                                                String description) {
        return jawupayClient.initiatePayout(new JawupayPayoutRequest(
                operationId,
                idempotencyKey,
                amount,
                currency,
                accountHolderId,
                actorUserId,
                destinationPhone,
                description
        ));
    }

    public JawupayPaymentStatusResponse fetchPaymentStatus(String pspReference) {
        return jawupayClient.fetchPaymentStatus(pspReference);
    }
}
