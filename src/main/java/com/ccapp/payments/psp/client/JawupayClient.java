package com.ccapp.payments.psp.client;

import com.ccapp.payments.psp.dto.JawupayInitiateRequest;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;

public interface JawupayClient {
    JawupayInitiateResponse initiatePayment(JawupayInitiateRequest request);
}
