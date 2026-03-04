package com.ccapp.psp.client;

import com.ccapp.psp.dto.JawupayInitiateRequest;
import com.ccapp.psp.dto.JawupayInitiateResponse;

public interface JawupayClient {
    JawupayInitiateResponse initiatePayment(JawupayInitiateRequest request);
}
