package com.ccapp.mobile.sponsor.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Locks the sponsor eligibility rule:
 *
 *   eligible = (accountHolder is in the MLM network)
 *
 * Phone verification, wallet status, membership status, and payment history
 * must NOT influence the result.
 */
@ExtendWith(MockitoExtension.class)
class SponsorEligibilityValidatorTest {

    @Mock
    private MlmRelationshipRepository mlmRelationshipRepository;

    @InjectMocks
    private SponsorEligibilityValidator validator;

    private UUID accountHolderId;
    private AccountHolder accountHolder;

    @BeforeEach
    void setUp() {
        accountHolderId = UUID.randomUUID();
        accountHolder = new AccountHolder();
        accountHolder.setId(accountHolderId);
    }

    // -------------------------------------------------------------------------
    // Core network-membership rule
    // -------------------------------------------------------------------------

    @Test
    void eligible_when_in_network() {
        when(mlmRelationshipRepository.existsByDescendantId(accountHolderId)).thenReturn(true);

        assertThat(validator.isEligible(accountHolder)).isTrue();
    }

    @Test
    void not_eligible_when_not_in_network() {
        when(mlmRelationshipRepository.existsByDescendantId(accountHolderId)).thenReturn(false);

        assertThat(validator.isEligible(accountHolder)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Phone verification must NOT affect eligibility
    // -------------------------------------------------------------------------

    @Test
    void eligible_when_in_network_and_phone_not_verified() {
        accountHolder.setPhoneVerifiedAt(null);
        when(mlmRelationshipRepository.existsByDescendantId(accountHolderId)).thenReturn(true);

        assertThat(validator.isEligible(accountHolder)).isTrue();
    }

    @Test
    void eligible_when_in_network_and_phone_verified() {
        accountHolder.setPhoneVerifiedAt(OffsetDateTime.now());
        when(mlmRelationshipRepository.existsByDescendantId(accountHolderId)).thenReturn(true);

        assertThat(validator.isEligible(accountHolder)).isTrue();
    }

    // -------------------------------------------------------------------------
    // Wallet / membership / financial status must NOT affect eligibility
    // — verified by the absence of FinancialAccessPolicy in the validator;
    //   the following tests confirm the contract is network-only.
    // -------------------------------------------------------------------------

    @Test
    void eligible_regardless_of_wallet_or_membership_status() {
        // No financial access mock needed — the validator no longer calls it.
        // If it did, Mockito strict-stubbing would fail this test immediately.
        when(mlmRelationshipRepository.existsByDescendantId(accountHolderId)).thenReturn(true);

        assertThat(validator.isEligible(accountHolder)).isTrue();
    }

    @Test
    void not_eligible_regardless_of_wallet_or_membership_status_when_not_in_network() {
        when(mlmRelationshipRepository.existsByDescendantId(accountHolderId)).thenReturn(false);

        assertThat(validator.isEligible(accountHolder)).isFalse();
    }
}
