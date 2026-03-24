package com.ccapp.admin.dto;

import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminSubscriptionPlanRequest(
        UUID id,
        @NotNull SubscriptionGrade formulaType,
        @NotNull Integer durationMonths,
        @Size(min = 2, max = 3) String countryCode,
        @NotBlank @Size(min = 3, max = 3) String currency,
        BigDecimal monthlyPrice,
        BigDecimal totalPrice,
        boolean isActive
) {
}
