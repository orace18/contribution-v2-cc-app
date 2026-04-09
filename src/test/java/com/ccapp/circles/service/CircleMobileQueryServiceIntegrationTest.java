package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleCycle;
import com.ccapp.circles.domain.entity.CircleLoan;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.entity.CircleRepayment;
import com.ccapp.circles.domain.enums.CircleCycleStatus;
import com.ccapp.circles.domain.enums.CircleLoanStatus;
import com.ccapp.circles.domain.enums.CircleMemberInteractionType;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleRepaymentStatus;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.CircleListItemResponse;
import com.ccapp.circles.dto.CircleMemberFinancialsResponse;
import com.ccapp.circles.repository.CircleCycleRepository;
import com.ccapp.circles.repository.CircleLoanRepository;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepaymentRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.circlesocial.domain.entity.CircleProgress;
import com.ccapp.circlesocial.repository.CircleProgressRepository;
import com.ccapp.chat.service.ChatConversationService;
import com.ccapp.chat.domain.entity.ChatMessage;
import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.seedcapital.readiness.repository.CircleMeetingRepository;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.ccapp.seedcapital.readiness.service.CircleTrustScoreService;
import com.ccapp.seedcapital.readiness.service.SeedCapitalCreditBridgeService;
import com.ccapp.seedcapital.savings.service.ImfSavingsGateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(CircleMobileQueryService.class)
class CircleMobileQueryServiceIntegrationTest {

    @Autowired
    private CircleMobileQueryService circleMobileQueryService;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private CircleMemberRepository circleMemberRepository;
    @Autowired
    private CircleCycleRepository circleCycleRepository;
    @Autowired
    private CircleLoanRepository circleLoanRepository;
    @Autowired
    private CircleRepaymentRepository circleRepaymentRepository;
    @Autowired
    private CircleProgressRepository circleProgressRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private CircleReadinessAccessService circleReadinessAccessService;
    @MockBean
    private CircleMeetingRepository circleMeetingRepository;
    @MockBean
    private CircleTrustScoreService circleTrustScoreService;
    @MockBean
    private ImfSavingsGateService imfSavingsGateService;
    @MockBean
    private SeedCapitalCreditBridgeService seedCapitalCreditBridgeService;
    @MockBean
    private ChatConversationService chatConversationService;
    @MockBean
    private CircleMeetingPolicyService circleMeetingPolicyService;

    @BeforeEach
    void setUp() {
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID accountHolderId = invocation.getArgument(0);
            return accountHolderRepository.findById(accountHolderId).orElseThrow();
        });
    }

    @Test
    void listUserCircles_shouldReturnRoleProgressUnreadAndLastActivity() {
        UUID requesterId = createAccountHolder("Coach", "User");
        UUID memberId = createAccountHolder("Member", "User");
        Circle circle = createCircle("Alpha Circle");
        createMember(circle, requesterId, CircleMemberRole.COACH, false);
        createMember(circle, memberId, CircleMemberRole.MEMBER, true);
        circleProgressRepository.save(CircleProgress.builder()
                .circleId(circle.getId())
                .membersCount(1)
                .membersCompletedTraining(1)
                .membersSubmittedProject(0)
                .membersPaidContribution(0)
                .progressPercentage(new BigDecimal("33.33"))
                .build());

        chatMessageRepository.save(ChatMessage.builder()
                .senderAccountHolderId(memberId)
                .recipientAccountHolderId(requesterId)
                .conversationId(UUID.randomUUID())
                .content("Unread message")
                .channel("IN_APP_CHAT")
                .messageStatus(ChatMessageStatus.SENT)
                .build());
        chatMessageRepository.save(ChatMessage.builder()
                .senderAccountHolderId(requesterId)
                .recipientAccountHolderId(memberId)
                .conversationId(UUID.randomUUID())
                .content("Read message")
                .channel("IN_APP_CHAT")
                .messageStatus(ChatMessageStatus.READ)
                .createdAt(OffsetDateTime.now().plusMinutes(1))
                .build());

        PageResponse<CircleListItemResponse> page = circleMobileQueryService.listUserCircles(requesterId, CircleStatus.ACTIVE, null, 0, 20);

        assertEquals(1, page.items().size());
        CircleListItemResponse item = page.items().get(0);
        assertEquals(circle.getId(), item.circleId());
        assertEquals(CircleMemberRole.COACH, item.role());
        assertEquals(1L, item.unreadMessages());
        assertEquals(0, new BigDecimal("33.33").compareTo(item.progress()));
    }

    @Test
    void getMemberFinancials_shouldComputeRemainingAndNextInstallment() {
        UUID requesterId = createAccountHolder("Coach", "User");
        UUID memberId = createAccountHolder("Member", "User");
        Circle circle = createCircle("Finance Circle");
        createMember(circle, requesterId, CircleMemberRole.COACH, false);
        CircleMember member = createMember(circle, memberId, CircleMemberRole.MEMBER, true);

        CircleCycle cycle = circleCycleRepository.save(CircleCycle.builder()
                .circle(circle)
                .cycleNumber(1)
                .startDate(LocalDate.now().minusDays(7))
                .endDate(LocalDate.now().plusDays(30))
                .status(CircleCycleStatus.ACTIVE)
                .build());
        CircleLoan loan = circleLoanRepository.save(CircleLoan.builder()
                .cycle(cycle)
                .borrower(member)
                .amount(new BigDecimal("100.00"))
                .interest(new BigDecimal("10.00"))
                .status(CircleLoanStatus.DISBURSED)
                .approvedAt(OffsetDateTime.now().minusDays(5))
                .disbursedAt(OffsetDateTime.now().minusDays(5))
                .build());
        circleRepaymentRepository.save(CircleRepayment.builder()
                .loan(loan)
                .amount(new BigDecimal("30.00"))
                .dueDate(LocalDate.now().minusDays(1))
                .paidAt(OffsetDateTime.now().minusDays(1))
                .status(CircleRepaymentStatus.PAID)
                .build());
        circleRepaymentRepository.save(CircleRepayment.builder()
                .loan(loan)
                .amount(new BigDecimal("20.00"))
                .dueDate(LocalDate.now().plusDays(3))
                .status(CircleRepaymentStatus.PENDING)
                .build());

        CircleMemberFinancialsResponse response = circleMobileQueryService.getMemberFinancials(requesterId, circle.getId(), memberId);

        assertEquals(0, new BigDecimal("100.00").compareTo(response.loanReceived()));
        assertEquals(0, new BigDecimal("80.0000").compareTo(response.remainingBalance()));
        assertEquals(1L, response.installmentsPaid());
        assertEquals(LocalDate.now().plusDays(3), response.nextInstallmentDate());
        assertEquals("DISBURSED", response.status());
    }

    @Test
    void createInteraction_shouldRejectSecondInteractionOnSameDay() {
        UUID requesterId = createAccountHolder("Coach", "User");
        UUID memberId = createAccountHolder("Member", "User");
        Circle circle = createCircle("Interaction Circle");
        createMember(circle, requesterId, CircleMemberRole.COACH, false);
        createMember(circle, memberId, CircleMemberRole.MEMBER, true);

        circleMobileQueryService.createInteraction(
                requesterId,
                circle.getId(),
                memberId,
                CircleMemberInteractionType.ENCOURAGE
        );

        assertThrows(
                BusinessException.class,
                () -> circleMobileQueryService.createInteraction(
                        requesterId,
                        circle.getId(),
                        memberId,
                        CircleMemberInteractionType.PRAY
                )
        );
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

    private Circle createCircle(String name) {
        return circleRepository.save(Circle.builder()
                .name(name)
                .coachId(UUID.randomUUID())
                .countryCode("BJ")
                .status(CircleStatus.ACTIVE)
                .build());
    }

    private CircleMember createMember(Circle circle, UUID accountHolderId, CircleMemberRole role, boolean financialMember) {
        return circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .accountHolderId(accountHolderId)
                .role(role)
                .financialMember(financialMember)
                .joinedAt(OffsetDateTime.now())
                .build());
    }
}
