package com.ccapp.cpass.otp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record CPassOtpSendResponse(
        @JsonProperty("referenceId") String referenceId,
        @JsonProperty("expiresAt") OffsetDateTime expiresAt
) {}
