package com.ccapp.circles.service;

import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.CircleMemberResponse;
import com.ccapp.circles.dto.CircleResponse;
import com.ccapp.circles.dto.CreateCircleRequest;
import com.ccapp.circles.dto.JoinCircleRequest;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.service.DelegationService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CircleService.class, TestJacksonAndCachingConfig.class})
class CircleServiceIntegrationTest {

    @Autowired
    private CircleService circleService;
    @Autowired
    private CircleMemberRepository circleMemberRepository;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MembershipRepository membershipRepository;

    @MockBean
    private DomainEventPublisher domainEventPublisher;
    @MockBean
    private CountryContextService countryContextService;
    @MockBean
    private DelegationService delegationService;
    @MockBean
    private CountryConfigResolver countryConfigResolver;
    @MockBean
    private com.ccapp.chat.service.ChatConversationService chatConversationService;
    @MockBean
    private com.ccapp.credit.repository.LoanRepository loanRepository;
    @MockBean
    private com.ccapp.identity.repository.UserProfileRepository userProfileRepository;
    @MockBean
    private com.ccapp.seedcapital.readiness.service.CircleCoachAssignmentService circleCoachAssignmentService;
    // UserRepository and MembershipRepository no longer injected into CircleService —
    // gating removed from circle creation and joining.

    @BeforeEach
    void setUp() {
        when(countryContextService.resolve(any())).thenAnswer(invocation -> new CountryContext("BJ", "BJ", "XOF"));
        when(countryConfigResolver.resolveActiveConfig(any(), any())).thenAnswer(invocation -> new CountryConfigResolver.ResolvedConfig(
                invocation.getArgument(0),
                invocation.getArgument(1),
                1,
                "{}",
                CountryConfigResolver.ConfigSource.EMPTY
        ));
        when(countryConfigResolver.payloadAsObjectNode(any(CountryConfigResolver.ResolvedConfig.class)))
                .thenReturn(JsonNodeFactory.instance.objectNode());
        when(chatConversationService.getOrCreateCircleConversationId(any())).thenReturn(UUID.randomUUID());
        when(circleCoachAssignmentService.findAssignedCoachAccountHolderId(any())).thenReturn(Optional.empty());
    }

    @Test
    void createCircle_withoutKyc_shouldSucceed() {
        // KYC is no longer a prerequisite — circle is the onboarding environment
        UUID creatorId = createAccountHolder("Creator", "NoKyc", true);
        createUserMapping(creatorId, AccountStatus.ACTIVE); // KycStatus.NOT_STARTED by default

        CircleResponse response = circleService.createCircle(
                creatorId,
                new CreateCircleRequest("Cercle Sans KYC")
        );

        assertNotNull(response.id());
        assertEquals(CircleStatus.ACTIVE, response.status());
    }

    @Test
    void createCircle_withoutPhoneVerification_shouldSucceed() {
        // Phone verification is no longer a prerequisite for circle creation
        UUID creatorId = createAccountHolder("Creator", "NoPhone", false);
        createUserMapping(creatorId, AccountStatus.ACTIVE);

        CircleResponse response = circleService.createCircle(
                creatorId,
                new CreateCircleRequest("Cercle Sans Tel Verifie")
        );

        assertNotNull(response.id());
        assertEquals(CircleStatus.ACTIVE, response.status());
    }

    @Test
    void createCircle_withoutMembership_shouldSucceed() {
        // Membership is no longer a prerequisite for circle creation
        UUID creatorId = createAccountHolder("Creator", "NoMembership", true);
        createUserMapping(creatorId, AccountStatus.ACTIVE);
        // No membership created

        CircleResponse response = circleService.createCircle(
                creatorId,
                new CreateCircleRequest("Cercle Sans Abonnement")
        );

        assertNotNull(response.id());
        assertEquals(CircleStatus.ACTIVE, response.status());
        assertEquals(1, response.membersCount());
        assertTrue(circleRepository.existsActiveCircleForUser(creatorId));
    }

    @Test
    void createCircle_shouldSucceedWhenPhoneVerifiedAndMembershipActive() {
        UUID creatorId = createAccountHolder("Creator", "Eligible", true);
        createUserMapping(creatorId, AccountStatus.ACTIVE);
        createActiveMembership(creatorId);

        CircleResponse response = circleService.createCircle(
                creatorId,
                new CreateCircleRequest("Les Batisseurs")
        );

        assertNotNull(response.id());
        assertEquals(CircleStatus.ACTIVE, response.status());
        assertEquals(1, response.membersCount());
        assertTrue(circleRepository.existsActiveCircleForUser(creatorId));
        assertEquals(1, circleMemberRepository.countByCircle_Id(response.id()));
        var coachMembership = circleMemberRepository.findByCircle_IdAndRole(response.id(), CircleMemberRole.COACH);
        assertTrue(coachMembership.isPresent());
        assertFalse(coachMembership.get().isFinancialMember());
    }

    @Test
    void joinCircle_withoutMembership_shouldSucceed() {
        // Membership is no longer required to join a circle
        UUID creatorId = createAccountHolder("Creator", "Owner", true);
        createUserMapping(creatorId, AccountStatus.ACTIVE);
        CircleResponse circle = circleService.createCircle(
                creatorId,
                new CreateCircleRequest("Cercle Test Join")
        );

        UUID joinerId = createAccountHolder("Joiner", "NoMembership", true);
        createUserMapping(joinerId, AccountStatus.ACTIVE);
        // No membership for joiner

        CircleMemberResponse member = circleService.joinCircle(
                joinerId,
                circle.id(),
                new JoinCircleRequest("Commerce", "UNKNOWN", new BigDecimal("100.00"))
        );

        assertNotNull(member.id());
        assertEquals(2, circleMemberRepository.countByCircle_Id(circle.id()));
    }

    private UUID createAccountHolder(String firstName, String lastName, boolean phoneVerified) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .phoneVerifiedAt(phoneVerified ? OffsetDateTime.now().minusDays(1) : null)
                .build()).getId();
    }

    private void createUserMapping(UUID accountHolderId, AccountStatus status) {
        User user = userRepository.save(User.builder()
                .firebaseUid("uid-" + UUID.randomUUID())
                .email("user+" + UUID.randomUUID() + "@dev.local")
                .role(UserRole.USER)
                .accountStatus(status)
                .kycStatus(KycStatus.NOT_STARTED)
                .build());
        appUserRepository.save(AppUser.builder()
                .id(user.getId())
                .firebaseUid(user.getFirebaseUid())
                .accountHolderId(accountHolderId)
                .role(UserRole.USER)
                .build());
    }

    private void createActiveMembership(UUID accountHolderId) {
        membershipRepository.save(Membership.builder()
                .operationId(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .planCode("CLUB_PLUS")
                .countryCode("BJ")
                .currency("XOF")
                .amount(BigDecimal.ZERO.setScale(4))
                .status(MembershipStatus.ACTIVE)
                .activatedAt(OffsetDateTime.now().minusDays(1))
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build());
    }
}
