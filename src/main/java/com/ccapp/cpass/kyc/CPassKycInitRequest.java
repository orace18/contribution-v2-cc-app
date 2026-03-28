package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CPassKycInitRequest(
        @JsonProperty("userId") String userId,
        @JsonProperty("fullName") String fullName,
        @JsonProperty("phoneNumber") String phoneNumber,
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("requestedLevel") String requestedLevel
) {}
