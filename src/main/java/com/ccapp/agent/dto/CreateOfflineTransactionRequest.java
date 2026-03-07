package com.ccapp.agent.dto;

import com.ccapp.agent.domain.enums.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOfflineTransactionRequest(
        @NotNull(message = "accountHolderId is required")
        UUID accountHolderId,

        @NotNull(message = "transactionType is required")
        TransactionType transactionType,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be > 0")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be ISO-3")
        String currency
) {
}
