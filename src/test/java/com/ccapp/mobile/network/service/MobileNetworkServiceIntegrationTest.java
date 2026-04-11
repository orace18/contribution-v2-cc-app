package com.ccapp.mobile.network.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.OnboardingStep;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import com.ccapp.mobile.onboarding.domain.enums.MobileOnboardingJourneyStatus;
import com.ccapp.mobile.onboarding.repository.MobileOnboardingJourneyRepository;
import com.ccapp.mobile.network.dto.MobileNetworkMembersResponse;
import com.ccapp.mobile.network.dto.MobileNetworkMembershipStatus;
import com.ccapp.mobile.network.dto.MobileNetworkTeamStatsResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTreeView;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.ccapp.payments.subscription.service.MembershipReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(MobileNetworkService.class)
class MobileNetworkServiceIntegrationTest {

    @Autowired
    private MobileNetworkService mobileNetworkService;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private MlmMemberLinkRepository mlmMemberLinkRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private MobileOnboardingJourneyRepository mobileOnboardingJourneyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private MembershipReconciliationService membershipReconciliationService;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
    }

    @Test
    void shouldSupportSponsorAndPlacementViewsPlusFollowUps() {
        UUID actorUserId = UUID.randomUUID();

        UUID leaderId = createAccountHolder("Leader", "Root", "+22997000000");
        UUID directActiveId = createAccountHolder("Thomas", "SANKARA", "+22997000001");
        UUID directFollowUpId = createAccountHolder("Fatou", "DIALLO", "+22997000002");
        UUID secondLevelId = createAccountHolder("Noah", "SOSSA", "+22997000003");

        createMemberLink(leaderId, null, leaderId);
        createMemberLink(directActiveId, leaderId, leaderId);
        createMemberLink(directFollowUpId, leaderId, directActiveId);
        createMemberLink(secondLevelId, directActiveId, directFollowUpId);

        createMembership(leaderId, MembershipStatus.ACTIVE, OffsetDateTime.now().minusDays(30), OffsetDateTime.now().plusDays(60));
        createMembership(directActiveId, MembershipStatus.ACTIVE, OffsetDateTime.now().minusDays(10), OffsetDateTime.now().plusDays(20));
        createMembership(directFollowUpId, MembershipStatus.EXPIRED, OffsetDateTime.now().minusDays(40), OffsetDateTime.now().minusDays(7));
        createMembership(secondLevelId, MembershipStatus.ACTIVE, OffsetDateTime.now().minusHours(3), OffsetDateTime.now().plusDays(3));

        createAvatar(directActiveId, UUID.fromString("11111111-1111-1111-1111-111111111111"));

        MobileNetworkTeamStatsResponse stats = mobileNetworkService.getTeamStats(actorUserId, leaderId);
        assertEquals(2L, stats.active());
        assertEquals(1L, stats.inactive());
        assertEquals(1L, stats.toFollowUp());

        MobileNetworkMembersResponse directs = mobileNetworkService.getDirectMembers(actorUserId, leaderId);
        assertEquals(2, directs.members().size());
        var directByName = directs.members().stream()
                .sorted(Comparator.comparing(member -> member.firstName() == null ? "" : member.firstName()))
                .toList();
        assertEquals("Fatou", directByName.get(0).firstName());
        assertEquals(MobileNetworkMembershipStatus.TO_FOLLOW_UP, directByName.get(0).membershipStatus());
        assertEquals("Thomas", directByName.get(1).firstName());
        assertEquals(1L, directByName.get(1).downlineCount());
        assertEquals(MobileNetworkMembershipStatus.ACTIVE, directByName.get(1).membershipStatus());
        assertNotNull(directByName.get(1).avatar());

        var activity = mobileNetworkService.getTodayActivity(actorUserId, leaderId);
        assertEquals(3L, activity.newMembers());
        assertEquals(1L, activity.subscriptionsPaid());

        var followUps = mobileNetworkService.getFollowUps(actorUserId, leaderId);
        assertEquals(1, followUps.size());
        assertEquals("Fatou DIALLO", followUps.get(0).name());
        assertTrue(followUps.get(0).inactiveDays() >= 7);

        var genealogy = mobileNetworkService.getTree(actorUserId, leaderId, MobileNetworkTreeView.GENEALOGY);
        assertEquals(3L, genealogy.totalMembers());
        assertEquals(2, genealogy.root().children().size());
        var genealogyChildren = genealogy.root().children().stream()
                .sorted(Comparator.comparing(child -> child.name() == null ? "" : child.name()))
                .toList();
        assertEquals("Fatou DIALLO", genealogyChildren.get(0).name());
        assertEquals("Thomas SANKARA", genealogyChildren.get(1).name());
        assertEquals(1, genealogyChildren.get(1).children().size());

        var placement = mobileNetworkService.getTree(actorUserId, leaderId, MobileNetworkTreeView.PLACEMENT);
        assertEquals(1, placement.root().children().size());
        assertEquals("Thomas SANKARA", placement.root().children().get(0).name());
        assertEquals(1, placement.root().children().get(0).children().size());
        assertEquals("Fatou DIALLO", placement.root().children().get(0).children().get(0).name());

        var search = mobileNetworkService.search(actorUserId, leaderId, "00002");
        assertEquals(1, search.members().size());
        assertEquals("Fatou", search.members().get(0).firstName());

        var blankSearch = mobileNetworkService.search(actorUserId, leaderId, "   ");
        assertTrue(blankSearch.members().isEmpty());
    }

    @Test
    void sponsorPendingShouldBlockEligibilityUntilDecisionIsMade() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = createAccountHolder(
                "Samuel",
                "KIKI",
                "+22997000076",
                true
        );

        mobileOnboardingJourneyRepository.save(MobileOnboardingJourney.builder()
                .accountHolderId(accountHolderId)
                .status(MobileOnboardingJourneyStatus.COUNTRY_SELECTED)
                .countryCode("BJ")
                .sponsorSet(false)
                .sponsorAccountHolderId(null)
                .networkLinked(false)
                .onboardingCompleted(false)
                .build());

        createMembership(
                accountHolderId,
                MembershipStatus.ACTIVE,
                OffsetDateTime.now().minusDays(10),
                OffsetDateTime.now().plusDays(20)
        );

        var eligibility = mobileNetworkService.getEligibility(actorUserId, accountHolderId);

        assertFalse(eligibility.hasSponsor());
        assertFalse(eligibility.sponsorResolved());
        assertTrue(eligibility.phoneVerified());
        assertTrue(eligibility.hasPaidMembership());
        assertEquals("SPONSOR_PENDING", eligibility.sponsorDecision());
        assertFalse(eligibility.isEligible());
    }

    @Test
    void sponsorSelectedShouldSatisfySponsorRequirement() {
        UUID actorUserId = UUID.randomUUID();
        UUID sponsorId = createAccountHolder("Parent", "Reseau", "+22997000078", true);
        UUID accountHolderId = createAccountHolder(
                "Samuel",
                "KIKI",
                "+22997000079",
                true
        );

        mobileOnboardingJourneyRepository.save(MobileOnboardingJourney.builder()
                .accountHolderId(accountHolderId)
                .status(MobileOnboardingJourneyStatus.SPONSOR_SET)
                .countryCode("BJ")
                .sponsorSet(true)
                .sponsorAccountHolderId(sponsorId)
                .networkLinked(false)
                .onboardingCompleted(true)
                .build());

        createMembership(
                accountHolderId,
                MembershipStatus.ACTIVE,
                OffsetDateTime.now().minusDays(10),
                OffsetDateTime.now().plusDays(20)
        );

        var eligibility = mobileNetworkService.getEligibility(actorUserId, accountHolderId);

        assertTrue(eligibility.hasSponsor());
        assertTrue(eligibility.sponsorResolved());
        assertEquals("SPONSOR_SELECTED", eligibility.sponsorDecision());
        assertFalse(eligibility.isEligible());
    }

    @Test
    void sponsorDeclinedShouldSatisfySponsorRequirementWithoutClaimingSponsorPresence() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = createAccountHolder(
                "Samuel",
                "KIKI",
                "+22997000077",
                true
        );

        mobileOnboardingJourneyRepository.save(MobileOnboardingJourney.builder()
                .accountHolderId(accountHolderId)
                .status(MobileOnboardingJourneyStatus.SPONSOR_SET)
                .countryCode("BJ")
                .sponsorSet(true)
                .sponsorAccountHolderId(null)
                .networkLinked(false)
                .onboardingCompleted(true)
                .build());

        createMembership(
                accountHolderId,
                MembershipStatus.ACTIVE,
                OffsetDateTime.now().minusDays(10),
                OffsetDateTime.now().plusDays(20)
        );

        var eligibility = mobileNetworkService.getEligibility(actorUserId, accountHolderId);

        assertFalse(eligibility.hasSponsor());
        assertTrue(eligibility.sponsorResolved());
        assertTrue(eligibility.phoneVerified());
        assertTrue(eligibility.hasPaidMembership());
        assertEquals("SPONSOR_DECLINED", eligibility.sponsorDecision());
        assertEquals("PENDING_PLACEMENT", eligibility.placementStatus());
        assertFalse(eligibility.isEligible());
    }

    private UUID createAccountHolder(String firstName, String lastName, String phoneNumber) {
        return createAccountHolder(firstName, lastName, phoneNumber, false);
    }

    private UUID createAccountHolder(String firstName,
                                     String lastName,
                                     String phoneNumber,
                                     boolean phoneVerified) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber(phoneNumber)
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .phoneVerifiedAt(phoneVerified ? OffsetDateTime.now().minusDays(1) : null)
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }

    private void createMemberLink(UUID accountHolderId, UUID sponsorAccountHolderId, UUID placementAccountHolderId) {
        mlmMemberLinkRepository.save(MlmMemberLink.builder()
                .accountHolderId(accountHolderId)
                .sponsorAccountHolderId(sponsorAccountHolderId)
                .placementAccountHolderId(placementAccountHolderId)
                .build());
    }

    private void createMembership(UUID accountHolderId,
                                  MembershipStatus status,
                                  OffsetDateTime activatedAt,
                                  OffsetDateTime expiresAt) {
        membershipRepository.save(Membership.builder()
                .operationId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .planCode("PLAN")
                .planGrade(SubscriptionGrade.GOLD)
                .durationMonths(1)
                .countryCode("BJ")
                .currency("XOF")
                .amount(java.math.BigDecimal.valueOf(10000))
                .status(status)
                .activatedAt(activatedAt)
                .expiresAt(expiresAt)
                .build());
    }

    private void createAvatar(UUID accountHolderId, UUID mediaId) {
        UUID firebaseRef = UUID.randomUUID();
        User user = userRepository.save(User.builder()
                .firebaseUid("firebase-" + firebaseRef)
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .build());

        appUserRepository.save(AppUser.builder()
                .id(user.getId())
                .firebaseUid(user.getFirebaseUid())
                .accountHolderId(accountHolderId)
                .role(UserRole.USER)
                .build());

        UserProfile profile = UserProfile.builder()
                .user(user)
                .firstName("Thomas")
                .lastName("SANKARA")
                .profilePhotoMediaId(mediaId)
                .onboardingStep(OnboardingStep.STEP_1)
                .onboardingCompleted(false)
                .solidarityGuaranteeAccepted(false)
                .termsAccepted(false)
                .build();
        userProfileRepository.save(profile);
    }
}
