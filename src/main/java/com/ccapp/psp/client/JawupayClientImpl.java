package com.ccapp.psp.client;

import com.ccapp.psp.dto.JawupayInitiateRequest;
import com.ccapp.psp.dto.JawupayInitiateResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JawupayClientImpl implements JawupayClient {

    @Override
    public JawupayInitiateResponse initiatePayment(JawupayInitiateRequest request) {
        String pspReference = "JAWU-" + UUID.randomUUID();
        String checkoutUrl = "https://checkout.jawupay.example/pay/" + pspReference;
        return new JawupayInitiateResponse(pspReference, checkoutUrl);
    }
}
