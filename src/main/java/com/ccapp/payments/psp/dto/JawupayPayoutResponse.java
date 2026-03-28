package com.ccapp.payments.psp.dto;

/** Response from the legacy JawuPay mock payout endpoint ({@code /payouts/initiate}). */
public record JawupayPayoutResponse(
        String pspReference
) {
}
