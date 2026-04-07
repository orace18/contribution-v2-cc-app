package com.ccapp.mobile.home.service;

import com.ccapp.chat.domain.entity.ChatConversation;
import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.chat.repository.ChatConversationRepository;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.community.domain.entity.CommunityPost;
import com.ccapp.community.repository.CommunityPostRepository;
import com.ccapp.credit.domain.entity.CreditEligibility;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.CreditEligibilityRepository;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mobile.home.dto.MobileHomeResponse;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationStatus;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.repository.NotificationRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
@Import(MobileHomeService.class)
class MobileHomeServiceIntegrationTest {

    @Autowired
    private MobileHomeService mobileHomeService;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private OperationRepository operationRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private CreditEligibilityRepository creditEligibilityRepository;
    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private CircleMemberRepository circleMemberRepository;
    @Autowired
    private CommunityPostRepository communityPostRepository;
    @Autowired
    private ChatConversationRepository chatConversationRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean
    private AccessControlService accessControlService;
    @MockBean
    private AccountHolderService accountHolderService;

    @BeforeEach
    void setUp() {
        doNothing().when(accessControlService).assertCanActOnAccountHolder(any(), any(), any());
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
    }

    @Test
    void getHome_shouldAggregateCrossDomainData() {
        UUID actorUserId = UUID.randomUUID();
        UUID accountHolderId = createAccountHolder("Member", "One");
        UUID peerId = createAccountHolder("Member", "Two");
        UUID coachId = createAccountHolder("Coach", "One");

        operationRepository.save(Operation.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("10000"))
                .currency("XOF")
                .idempotencyKey("sub-" + UUID.randomUUID())
                .description("subscription")
                .build());
        membershipRepository.save(Membership.builder()
                .operationId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .planCode("CLASSIC_1M")
                .planGrade(SubscriptionGrade.CLASSIC)
                .durationMonths(1)
                .countryCode("BJ")
                .currency("XOF")
                .amount(new BigDecimal("10000"))
                .status(MembershipStatus.ACTIVE)
                .activatedAt(OffsetDateTime.now().minusDays(2))
                .expiresAt(OffsetDateTime.now().plusDays(28))
                .build());

        creditEligibilityRepository.save(CreditEligibility.builder()
                .accountHolderId(accountHolderId)
                .rmsScore(new BigDecimal("87.50"))
                .circleScore(new BigDecimal("80"))
                .eligibilityScore(new BigDecimal("84"))
                .maxEligibleAmount(new BigDecimal("50000"))
                .build());

        loanRepository.save(Loan.builder()
                .accountHolderId(accountHolderId)
                .circleId(UUID.randomUUID())
                .countryCode("BJ")
                .amount(new BigDecimal("15000"))
                .currency("XOF")
                .interestRate(new BigDecimal("8.5"))
                .durationMonths(6)
                .status(LoanStatus.ACTIVE)
                .build());

        Circle circle = circleRepository.save(Circle.builder()
                .name("Alpha Circle")
                .coachId(coachId)
                .countryCode("BJ")
                .status(CircleStatus.ACTIVE)
                .build());
        circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .accountHolderId(accountHolderId)
                .role(CircleMemberRole.MEMBER)
                .financialMember(true)
                .joinedAt(OffsetDateTime.now())
                .build());
        circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .accountHolderId(coachId)
                .role(CircleMemberRole.COACH)
                .financialMember(false)
                .joinedAt(OffsetDateTime.now())
                .build());

        communityPostRepository.save(CommunityPost.builder()
                .title("Featured post")
                .content("Important community update")
                .featured(true)
                .featuredPriority(1)
                .active(true)
                .build());

        ChatConversation conversation = chatConversationRepository.save(ChatConversation.builder()
                .participantAAccountHolderId(accountHolderId)
                .participantBAccountHolderId(peerId)
                .build());
        chatMessageRepository.save(ChatMessage.builder()
                .senderAccountHolderId(peerId)
                .recipientAccountHolderId(accountHolderId)
                .conversationId(conversation.getId())
                .content("Unread message")
                .channel("IN_APP_CHAT")
                .messageStatus(ChatMessageStatus.SENT)
                .build());

        notificationRepository.save(Notification.builder()
                .accountHolderId(accountHolderId)
                .actorUserId(actorUserId)
                .type(NotificationType.AGENT_ACTION)
                .channel(NotificationChannel.IN_APP)
                .templateCode("HOME_TEST")
                .message("Notification body")
                .targetId("wallet-home")
                .deepLinkRouteKey("WALLET_HOME")
                .deepLinkLabel("Open wallet")
                .deepLinkParamsJson("{\"tab\":\"overview\"}")
                .status(NotificationStatus.SENT)
                .build());

        MobileHomeResponse response = mobileHomeService.getHome(actorUserId, accountHolderId);

        assertEquals("ACTIVE", response.membership().status());
        assertNotNull(response.membership().expiresAt());
        assertTrue(response.membership().daysRemaining() >= 0);
        assertEquals(new BigDecimal("87.50"), response.rmsBalance());
        assertEquals(1, response.activeLoans().size());
        assertEquals(LoanStatus.ACTIVE, response.activeLoans().get(0).status());
        assertNotNull(response.circleSummary());
        assertEquals("Alpha Circle", response.circleSummary().name());
        assertEquals(1L, response.circleSummary().membersCount());
        assertTrue(response.communityHighlights().isEmpty());
        assertEquals(1L, response.unreadMessages());
        assertEquals(1L, response.notifications().total());
        assertEquals(1, response.notifications().latest().size());
        assertEquals("wallet-home", response.notifications().latest().getFirst().targetId());
        assertNotNull(response.notifications().latest().getFirst().deepLink());
        assertEquals("WALLET_HOME", response.notifications().latest().getFirst().deepLink().routeKey());
        assertEquals("overview", response.notifications().latest().getFirst().deepLink().params().get("tab"));
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }
}
