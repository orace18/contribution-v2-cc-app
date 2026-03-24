package com.ccapp.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminSubscriptionPlanBulkUpsertRequest(
        @NotBlank @Size(min = 2, max = 3) String countryCode,
        @NotEmpty List<@Valid AdminSubscriptionPlanRequest> plans
) {
}
