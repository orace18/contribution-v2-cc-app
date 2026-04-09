package com.ccapp.seedcapital.savings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConfigureImfSavingsChannelRequest(
        @NotNull UUID partnerId,
        @Size(max = 128) String partnerMemberReference,
        @Size(max = 128) String externalChannelReference,
        @Size(max = 500) String readinessNote
) {
}
