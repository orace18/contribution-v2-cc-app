package com.ccapp.mobile.sponsor.service;

import com.ccapp.financial.access.domain.FinancialAccessUseCase;
import com.ccapp.financial.access.service.FinancialAccessPolicy;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SponsorEligibilityValidator {

    private final FinancialAccessPolicy financialAccessPolicy;
    private final MlmRelationshipRepository mlmRelationshipRepository;

    /**
     * A sponsor is eligible only when ALL four conditions hold:
     * 1. Phone is verified (phoneVerifiedAt != null)
     * 2. An ACTIVE wallet exists
     * 3. An ACTIVE, non-expired membership exists
     * 4. The account holder appears in the MLM network tree
     */
    public boolean isEligible(AccountHolder accountHolder) {
        if (accountHolder.getPhoneVerifiedAt() == null) {
            return false;
        }

        if (!financialAccessPolicy.isAllowed(accountHolder.getId(), FinancialAccessUseCase.NETWORK_SPONSOR_ELIGIBILITY)) {
            return false;
        }

        return mlmRelationshipRepository.countByDescendantIdAndLevelGreaterThan(accountHolder.getId(), 0) > 0;
    }
}
