package com.ccapp.psp.dto;

public record JawupayInitiateResponse(
        String pspReference,
        String checkoutUrl
) {
}
