package com.ccapp.cpass.otp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CPassOtpVerifyResponse(
        @JsonProperty("verified") boolean verified,
        @JsonProperty("attemptsRemaining") int attemptsRemaining
) {}
