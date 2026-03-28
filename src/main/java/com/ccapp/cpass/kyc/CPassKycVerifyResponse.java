package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record CPassKycVerifyResponse(
        @JsonProperty("id") String id,
        @JsonProperty("referenceId") String referenceId,
        @JsonProperty("level") String level,
        @JsonProperty("status") String status,
        @JsonProperty("score") Double score,
        @JsonProperty("validUntil") OffsetDateTime validUntil,
        @JsonProperty("createdAt") OffsetDateTime createdAt
) {}
