package com.ccapp.financing.dto;

import com.ccapp.media.domain.enums.KycDocumentStatus;
import com.ccapp.media.domain.enums.KycDocumentType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminFinancingApplicationDocumentResponse(
        UUID id,
        UUID mediaFileId,
        KycDocumentType documentType,
        KycDocumentStatus status,
        OffsetDateTime submittedAt
) {
}
