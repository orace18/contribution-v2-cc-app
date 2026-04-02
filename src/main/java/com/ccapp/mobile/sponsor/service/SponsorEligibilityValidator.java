package com.ccapp.mobile.sponsor.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class SponsorEligibilityValidator {

    private static final String WALLET_ACTIVE = "ACTIVE";

    private final WalletRepository walletRepository;
    private final MembershipRepository membershipRepository;
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

        boolean walletActive = walletRepository
                .findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolder.getId(), WALLET_ACTIVE)
                .isPresent();
        if (!walletActive) {
            return false;
        }

        boolean membershipActive = membershipRepository
                .findFirstByAccountHolderIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        accountHolder.getId(), MembershipStatus.ACTIVE, OffsetDateTime.now())
                .isPresent();
        if (!membershipActive) {
            return false;
        }

        return mlmRelationshipRepository.countByDescendantIdAndLevelGreaterThan(accountHolder.getId(), 0) > 0;
    }
}
