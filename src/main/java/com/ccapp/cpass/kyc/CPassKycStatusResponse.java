package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CPassKycStatusResponse(
        @JsonProperty("caseId") String caseId,
        @JsonProperty("productStatus") String productStatus,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("nextAction") String nextAction,
        @JsonProperty("statusReason") String statusReason,
        @JsonProperty("recommendation") String recommendation,
        @JsonProperty("confidenceScore") Double confidenceScore
) {}
