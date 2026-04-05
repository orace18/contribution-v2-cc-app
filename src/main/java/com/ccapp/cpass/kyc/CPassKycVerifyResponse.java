package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CPassKycVerifyResponse(
        @JsonProperty("id") String id,
        @JsonProperty("referenceId") String referenceId,
        @JsonProperty("level") String level,
        @JsonProperty("status") String status,
        @JsonProperty("score") Double score,
        @JsonProperty("documentValid") Boolean documentValid,
        @JsonProperty("faceMatchScore") Double faceMatchScore,
        @JsonProperty("identityStatus") String identityStatus,
        @JsonProperty("riskLevel") String riskLevel,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName") String lastName,
        @JsonProperty("birthDate") LocalDate birthDate,
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("validUntil") OffsetDateTime validUntil,
        @JsonProperty("createdAt") OffsetDateTime createdAt
) {}
