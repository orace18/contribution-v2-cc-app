package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record CPassKycInitResponse(
        @JsonProperty("status") String status,
        @JsonProperty("kycLevel") String kycLevel,
        @JsonProperty("kycCaseId") String kycCaseId,
        @JsonProperty("referenceId") String referenceId,
        @JsonProperty("documentValid") Boolean documentValid,
        @JsonProperty("faceMatchScore") Double faceMatchScore,
        @JsonProperty("identityStatus") String identityStatus,
        @JsonProperty("riskLevel") String riskLevel,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName") String lastName,
        @JsonProperty("birthDate") LocalDate birthDate,
        @JsonProperty("documentNumber") String documentNumber
) {}
