package com.ccapp.identity.verification.dto;

public record FirebasePhoneVerificationResponse(
        boolean success,
        boolean verified,
        String phoneNumber
) {
}
