package com.ccapp.identity.dto;

import com.ccapp.identity.account_holder.domain.enums.AccountType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LinkAssistedAccountResponse(
        UUID appUserId,
        UUID accountHolderId,
        AccountType accountType,
        String countryCode,
        String serviceCountryCode,
        OffsetDateTime linkedAt
) {
}
