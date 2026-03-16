package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.OfferRequirementType;
import com.ccapp.financing.domain.enums.OfferVerificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OfferRequirementResponse(
        UUID id,
        UUID offerId,
        OfferRequirementType requirementType,
        boolean required,
        OfferVerificationType verificationType,
        OffsetDateTime createdAt
) {
}
