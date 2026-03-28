package com.ccapp.cpass.kyc;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CPassKycDocumentResponse(
        @JsonProperty("documentId") String documentId,
        @JsonProperty("status") String status,
        @JsonProperty("objectKey") String objectKey,
        @JsonProperty("validityStatus") String validityStatus
) {}
