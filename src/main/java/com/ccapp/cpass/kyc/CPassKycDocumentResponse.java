package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record CPassKycDocumentResponse(
        @JsonProperty("documentId") String documentId,
        @JsonProperty("status") String status,
        @JsonProperty("objectKey") String objectKey,
        @JsonProperty("validityStatus") String validityStatus,
        @JsonProperty("documentValid") Boolean documentValid,
        @JsonProperty("faceMatchScore") Double faceMatchScore,
        @JsonProperty("identityStatus") String identityStatus,
        @JsonProperty("riskLevel") String riskLevel,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName") String lastName,
        @JsonProperty("birthDate") LocalDate birthDate,
        @JsonProperty("documentNumber") String documentNumber
) {}
