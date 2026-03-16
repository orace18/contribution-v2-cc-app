package com.ccapp.financing.dto;

import com.ccapp.financing.domain.enums.OfferRequirementType;
import com.ccapp.financing.domain.enums.OfferVerificationType;
import jakarta.validation.constraints.NotNull;

public record OfferRequirementUpsertRequest(
        @NotNull OfferRequirementType requirementType,
        boolean required,
        @NotNull OfferVerificationType verificationType
) {
}
