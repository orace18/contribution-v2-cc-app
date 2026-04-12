package com.ccapp.mobile.sponsor.service;

import com.ccapp.circles.service.InviteCodeRateLimiter;
import com.ccapp.common.security.ClientRequestMetadata;
import com.ccapp.common.security.ClientRequestMetadataResolver;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mobile.sponsor.dto.SponsorLookupRequest;
import com.ccapp.mobile.sponsor.dto.SponsorLookupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the sponsor eligibility business rule:
 *
 *   eligible = (accountHolder is in the MLM network)
 *
 * Specifically verifies that the following conditions do NOT affect eligibility:
 *   - phoneVerifiedAt (null or set)
 *   - wallet status (absent)
 *   - membership status (absent)
 *   - payment history (absent)
 *
 * The MLM network entry is represented by the presence of an MlmRelationship row
 * with descendant_id = accountHolderId. This row is inserted atomically as part of
 * MLMPlacementService.placeAccountHolder() — a @Transactional method — so it only
 * exists in the database after a successfully committed placement. There is no
 * intermediate state where this row persists without a finalized placement.
 *
 * @see SponsorEligibilityValidator
 * @see com.ccapp.mlm.service.MLMPlacementService#placeAccountHolder
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({MobileSponsorLookupService.class, SponsorEligibilityValidator.class, PhoneValidator.class})
class MobileSponsorLookupServiceIntegrationTest {

    @Autowired
    private MobileSponsorLookupService sponsorLookupService;

    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @Autowired
    private MlmRelationshipRepository mlmRelationshipRepository;

    @MockBean
    private InviteCodeRateLimiter rateLimiter;

    @MockBean
    private ClientRequestMetadataResolver metadataResolver;

    private static final String TEST_PHONE = "+2290196969696";
    private static final String TEST_COUNTRY = "BJ";

    @BeforeEach
    void setUp() {
        when(rateLimiter.allow(any(), anyInt())).thenReturn(true);
        when(metadataResolver.current()).thenReturn(new ClientRequestMetadata("127.0.0.1", "BJ", null));
    }

    // -------------------------------------------------------------------------
    // Task 1 — account placed in network: eligible regardless of financial state
    // -------------------------------------------------------------------------

    @Test
    void eligible_when_in_network_and_phone_not_verified() {
        UUID accountHolderId = createAccountHolder(TEST_PHONE, null /* phoneVerifiedAt = null */);
        placeInNetwork(accountHolderId);

        SponsorLookupResponse response = sponsorLookupService.lookup(
                UUID.randomUUID(),
                new SponsorLookupRequest(TEST_PHONE, TEST_COUNTRY)
        );

        assertThat(response.found()).isTrue();
        assertThat(response.eligible()).isTrue();
        assertThat(response.accountHolderId()).isEqualTo(accountHolderId);
        assertThat(response.reasonCode()).isNull();
    }

    @Test
    void eligible_when_in_network_and_phone_verified() {
        UUID accountHolderId = createAccountHolder(TEST_PHONE, OffsetDateTime.now());
        placeInNetwork(accountHolderId);

        SponsorLookupResponse response = sponsorLookupService.lookup(
                UUID.randomUUID(),
                new SponsorLookupRequest(TEST_PHONE, TEST_COUNTRY)
        );

        assertThat(response.found()).isTrue();
        assertThat(response.eligible()).isTrue();
        assertThat(response.accountHolderId()).isEqualTo(accountHolderId);
    }

    @Test
    void eligible_when_in_network_with_no_wallet_and_no_membership() {
        // No wallet, no membership, no financial profile — only the MLM row matters.
        UUID accountHolderId = createAccountHolder(TEST_PHONE, null);
        placeInNetwork(accountHolderId);

        SponsorLookupResponse response = sponsorLookupService.lookup(
                UUID.randomUUID(),
                new SponsorLookupRequest(TEST_PHONE, TEST_COUNTRY)
        );

        assertThat(response.found()).isTrue();
        assertThat(response.eligible()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Task 2 — account NOT in network: found=true, eligible=false, NOT_IN_NETWORK
    // -------------------------------------------------------------------------

    @Test
    void not_eligible_with_reason_NOT_IN_NETWORK_when_no_mlm_row_exists() {
        // Account holder exists in DB but has never been placed in the MLM tree.
        createAccountHolder(TEST_PHONE, OffsetDateTime.now());

        SponsorLookupResponse response = sponsorLookupService.lookup(
                UUID.randomUUID(),
                new SponsorLookupRequest(TEST_PHONE, TEST_COUNTRY)
        );

        assertThat(response.found()).isTrue();
        assertThat(response.eligible()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("NOT_IN_NETWORK");
        assertThat(response.accountHolderId()).isNull();
    }

    @Test
    void not_found_when_phone_does_not_exist() {
        SponsorLookupResponse response = sponsorLookupService.lookup(
                UUID.randomUUID(),
                new SponsorLookupRequest("+2290100000001", TEST_COUNTRY)
        );

        assertThat(response.found()).isFalse();
        assertThat(response.eligible()).isNull();
        assertThat(response.reasonCode()).isEqualTo("NOT_FOUND");
    }

    // -------------------------------------------------------------------------
    // Task 3 — existsByDescendantId maps to finalized network entry
    //
    // MLMPlacementService.placeAccountHolder() runs inside @Transactional.
    // The first write it makes is ensureSelfRelationship(accountHolderId), which
    // inserts a self-reference row (ancestor=id, descendant=id, level=0).
    // That row, and all subsequent placement rows, are only visible after the
    // transaction commits. A rollback removes them entirely.
    //
    // Therefore: existsByDescendantId(id) == true iff placement committed.
    // There is no reachable intermediate state where this row exists without
    // a finalized network entry.
    // -------------------------------------------------------------------------

    @Test
    void existsByDescendantId_is_false_before_placement_and_true_after() {
        UUID accountHolderId = UUID.randomUUID();

        // Before placement: no rows in mlm_relationships
        assertThat(mlmRelationshipRepository.existsByDescendantId(accountHolderId)).isFalse();

        // Simulate a committed placeAccountHolder() by inserting the self-reference row
        // (ancestor=id, descendant=id, level=0) — this is the exact row that
        // MLMPlacementService.ensureSelfRelationship() inserts as its first write.
        placeInNetwork(accountHolderId);

        // After placement: row is visible — network entry is finalized
        assertThat(mlmRelationshipRepository.existsByDescendantId(accountHolderId)).isTrue();
    }

    @Test
    void existsByDescendantId_does_not_return_true_for_unrelated_account() {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        placeInNetwork(accountA);

        // Only accountA is in the network — accountB must not appear as a descendant
        assertThat(mlmRelationshipRepository.existsByDescendantId(accountA)).isTrue();
        assertThat(mlmRelationshipRepository.existsByDescendantId(accountB)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID createAccountHolder(String phone, OffsetDateTime phoneVerifiedAt) {
        AccountHolder saved = accountHolderRepository.save(
                AccountHolder.builder()
                        .id(UUID.randomUUID())
                        .firstName("Test")
                        .lastName("Sponsor")
                        .phoneNumber(phone)
                        .countryCode(TEST_COUNTRY)
                        .serviceCountryCode(TEST_COUNTRY)
                        .accountType(AccountType.STANDARD)
                        .createdVia(AccountCreatedVia.SELF)
                        .phoneVerifiedAt(phoneVerifiedAt)
                        .build()
        );
        return saved.getId();
    }

    /**
     * Inserts the self-reference row (ancestor=id, descendant=id, level=0) that
     * MLMPlacementService.ensureSelfRelationship() writes as part of a committed
     * placeAccountHolder() transaction. This is sufficient to make
     * existsByDescendantId(id) return true.
     */
    private void placeInNetwork(UUID accountHolderId) {
        mlmRelationshipRepository.save(
                MlmRelationship.builder()
                        .ancestorId(accountHolderId)
                        .descendantId(accountHolderId)
                        .level(0)
                        .createdAt(OffsetDateTime.now())
                        .build()
        );
    }
}
