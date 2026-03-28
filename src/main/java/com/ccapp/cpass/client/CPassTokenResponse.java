package com.ccapp.cpass.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CPassTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") int expiresIn
) {}
