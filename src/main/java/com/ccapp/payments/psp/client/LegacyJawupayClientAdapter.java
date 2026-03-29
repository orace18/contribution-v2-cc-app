package com.ccapp.payments.psp.client;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.payments.psp.dto.JawupayInitiateRequest;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
import com.ccapp.payments.psp.dto.JawupayPaymentStatusResponse;
import com.ccapp.payments.psp.dto.JawupayPayoutRequest;
import com.ccapp.payments.psp.dto.JawupayPayoutResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class LegacyJawupayClientAdapter implements JawupayClient {

    @Value("${psp.jawupay.mock-enabled:false}")
    private boolean mockEnabled;

    @Override
    public JawupayInitiateResponse initiatePayment(JawupayInitiateRequest request) {
        ensureMockModeEnabled("payments/initiate");
        log.info("PSP request provider=jawupay endpoint=/payments/initiate body={}", request);
        String pspReference = "JAWU-" + UUID.randomUUID();
        String checkoutUrl = "https://checkout.jawupay.example/pay/" + pspReference;
        JawupayInitiateResponse response = new JawupayInitiateResponse(pspReference, checkoutUrl);
        log.info("PSP response provider=jawupay endpoint=/payments/initiate body={}", response);
        return response;
    }

    @Override
    public JawupayPayoutResponse initiatePayout(JawupayPayoutRequest request) {
        ensureMockModeEnabled("payouts/initiate");
        log.info("PSP request provider=jawupay endpoint=/payouts/initiate body={}", request);
        String pspReference = "JAWU-PAYOUT-" + UUID.randomUUID();
        JawupayPayoutResponse response = new JawupayPayoutResponse(pspReference);
        log.info("PSP response provider=jawupay endpoint=/payouts/initiate body={}", response);
        return response;
    }

    @Override
    public JawupayPaymentStatusResponse fetchPaymentStatus(String pspReference) {
        ensureMockModeEnabled("payments/status");
        log.info("PSP request provider=jawupay endpoint=/payments/status pspReference={}", pspReference);
        JawupayPaymentStatusResponse response = new JawupayPaymentStatusResponse("PENDING");
        log.info("PSP response provider=jawupay endpoint=/payments/status body={}", response);
        return response;
    }

    private void ensureMockModeEnabled(String endpoint) {
        if (!mockEnabled) {
            throw new BusinessException(
                    "JAWUPAY_CLIENT_NOT_CONFIGURED: mock PSP is disabled and no real JawuPay adapter is implemented for " + endpoint
            );
        }
    }
}
