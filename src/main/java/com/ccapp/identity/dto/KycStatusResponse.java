package com.ccapp.identity.dto;

import com.ccapp.identity.domain.enums.KycStatus;

public record KycStatusResponse(
        KycStatus kycStatus
) {
}
