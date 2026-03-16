package com.ccapp.payments.psp.client;

import com.ccapp.payments.psp.dto.JawupayInitiateRequest;
import com.ccapp.payments.psp.dto.JawupayInitiateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class JawupayClientImpl implements JawupayClient {

    @Override
    public JawupayInitiateResponse initiatePayment(JawupayInitiateRequest request) {
        log.info("PSP request provider=jawupay endpoint=/payments/initiate body={}", request);
        String pspReference = "JAWU-" + UUID.randomUUID();
        String checkoutUrl = "https://checkout.jawupay.example/pay/" + pspReference;
        JawupayInitiateResponse response = new JawupayInitiateResponse(pspReference, checkoutUrl);
        log.info("PSP response provider=jawupay endpoint=/payments/initiate body={}", response);
        return response;
    }
}
