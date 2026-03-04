package com.ccapp.accounting.reconciliation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReconciliationImportRequest(
        @NotEmpty(message = "items are required")
        List<@Valid ReconciliationItemRequest> items
) {
}
