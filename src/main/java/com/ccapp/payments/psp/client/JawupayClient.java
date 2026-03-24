package com.ccapp.payments.psp.client;

import com.ccapp.payments.psp.dto.JawupayInitiateRequest;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
import com.ccapp.payments.psp.dto.JawupayPaymentStatusResponse;
import com.ccapp.payments.psp.dto.JawupayPayoutRequest;
import com.ccapp.payments.psp.dto.JawupayPayoutResponse;

public interface JawupayClient {
    JawupayInitiateResponse initiatePayment(JawupayInitiateRequest request);

    JawupayPayoutResponse initiatePayout(JawupayPayoutRequest request);

    JawupayPaymentStatusResponse fetchPaymentStatus(String pspReference);
}
