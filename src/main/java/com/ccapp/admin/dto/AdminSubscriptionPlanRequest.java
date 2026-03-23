package com.ccapp.admin.dto;

import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminSubscriptionPlanRequest(
        String code,
        String name,
        @NotNull SubscriptionGrade grade,
        @NotNull Integer durationMonths,
        @NotBlank @Size(min = 2, max = 3) String country,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin("0.0001") BigDecimal unitAmount,
        boolean active
) {
}
