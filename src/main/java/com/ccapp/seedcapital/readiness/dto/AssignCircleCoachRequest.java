package com.ccapp.seedcapital.readiness.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignCircleCoachRequest(
        @NotNull UUID coachAccountHolderId,
        @Size(max = 500) String note
) {
}
