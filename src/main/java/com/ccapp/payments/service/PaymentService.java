package com.ccapp.payments.service;

import com.ccapp.payments.psp.client.JawupayClient;
import com.ccapp.payments.psp.dto.JawupayInitiateRequest;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
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
}
