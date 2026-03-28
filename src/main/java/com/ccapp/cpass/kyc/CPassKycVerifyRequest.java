package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CPassKycVerifyRequest(
        @JsonProperty("caseId") String caseId,
        @JsonProperty("otpReferenceId") String otpReferenceId,
        @JsonProperty("otpCode") String otpCode
) {}
