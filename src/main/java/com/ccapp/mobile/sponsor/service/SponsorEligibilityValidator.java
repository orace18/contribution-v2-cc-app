package com.ccapp.mobile.sponsor.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SponsorEligibilityValidator {

    private final MlmRelationshipRepository mlmRelationshipRepository;

    /**
     * A sponsor is eligible if and only if their entry into the network has been finalized,
     * i.e. they appear in the MLM relationship table (networkStatus == JOINED).
     *
     * Phone verification, wallet status, membership status, and payment history
     * must NOT affect sponsor eligibility.
     */
    public boolean isEligible(AccountHolder accountHolder) {
        return mlmRelationshipRepository.existsByDescendantId(accountHolder.getId());
    }
}
