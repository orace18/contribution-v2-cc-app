package com.ccapp.seedcapital.savings.service;

import java.math.BigDecimal;
import java.util.UUID;

final class SavingsContributionPaymentIntentMetadata {

    private SavingsContributionPaymentIntentMetadata() {
    }

    static String buildDescription(UUID circleId, UUID cycleId) {
        return "IMF savings contribution circle=" + circleId + " cycle=" + cycleId;
    }

    static boolean matchesDescription(String description, UUID circleId, UUID cycleId) {
        return buildDescription(circleId, cycleId).equals(description);
    }

    static String buildFingerprint(UUID circleId,
                                   UUID cycleId,
                                   BigDecimal amount,
                                   String currency) {
        return "savings-contribution|"
                + circleId
                + "|"
                + cycleId
                + "|"
                + amount.stripTrailingZeros().toPlainString()
                + "|"
                + currency.trim().toUpperCase();
    }
}
