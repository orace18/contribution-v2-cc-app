package com.ccapp.financial.access.domain;

import java.util.List;
import java.util.UUID;

public record FinancialAccessDecision(
        UUID accountHolderId,
        FinancialAccessUseCase useCase,
        FinancialAccessState state,
        boolean allowed,
        List<FinancialAccessReasonCode> reasonCodes,
        List<String> messages,
        MemberFinancialProfile profile
) {
    public FinancialAccessDecision {
        reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        messages = List.copyOf(messages == null ? List.of() : messages);
    }

    public String primaryMessage() {
        return messages.isEmpty() ? "Financial access denied" : messages.get(0);
    }
}
