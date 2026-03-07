package com.ccapp.payments.psp.dto;

public record JawupayInitiateResponse(
        String pspReference,
        String checkoutUrl
) {
}
