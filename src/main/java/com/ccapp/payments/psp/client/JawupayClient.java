package com.ccapp.payments.psp.client;

import com.ccapp.payments.psp.dto.JawupayInitiateRequest;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
import com.ccapp.payments.psp.dto.JawupayPaymentStatusResponse;

public interface JawupayClient {
    JawupayInitiateResponse initiatePayment(JawupayInitiateRequest request);

    JawupayPaymentStatusResponse fetchPaymentStatus(String pspReference);
}
