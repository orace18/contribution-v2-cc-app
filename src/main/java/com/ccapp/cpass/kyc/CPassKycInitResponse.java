package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CPassKycInitResponse(
        @JsonProperty("status") String status,
        @JsonProperty("kycLevel") String kycLevel,
        @JsonProperty("kycCaseId") String kycCaseId,
        @JsonProperty("referenceId") String referenceId
) {}
