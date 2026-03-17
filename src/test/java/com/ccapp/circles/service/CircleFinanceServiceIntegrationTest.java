package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleLoan;
import com.ccapp.circles.domain.enums.CircleLoanStatus;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.*;
import com.ccapp.circles.repository.*;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CircleFinanceService.class, TestJacksonAndCachingConfig.class})
class CircleFinanceServiceIntegrationTest {

    @Autowired
    private CircleFinanceService circleFinanceService;
    @Autowired
    private CircleRepository circleRepository;
    @Autowired
    private CircleMemberRepository circleMemberRepository;
    @Autowired
    private CircleCycleRepository circleCycleRepository;
    @Autowired
    private CircleContributionRepository circleContributionRepository;
    @Autowired
    private CircleLoanRepository circleLoanRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @MockBean
    private CountryConfigResolver countryConfigResolver;

    @BeforeEach
    void setUp() {
        when(countryConfigResolver.resolveActiveConfig(any(), any())).thenAnswer(invocation -> new CountryConfigResolver.ResolvedConfig(
                invocation.getArgument(0),
                invocation.getArgument(1),
                null,
                "{}",
                CountryConfigResolver.ConfigSource.EMPTY
        ));
        when(countryConfigResolver.payloadAsObjectNode(any(CountryConfigResolver.ResolvedConfig.class)))
                .thenReturn(JsonNodeFactory.instance.objectNode());
    }

    @Test
    void createCycle_shouldCreateActiveCycleAndContributions() {
        Setup setup = seedCircleWithParticipants();

        CircleCycleResponse cycle = circleFinanceService.createCycle(
                setup.coachId(),
                setup.circle().getId(),
                new CreateCircleCycleRequest(LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("5000"))
        );

        assertNotNull(cycle.id());
        assertEquals(1, cycle.cycleNumber());
        assertEquals(com.ccapp.circles.domain.enums.CircleCycleStatus.ACTIVE, cycle.status());
        assertEquals(3, circleContributionRepository.countByCycle_Id(cycle.id()));
    }

    @Test
    void createCycle_shouldRejectWhenActiveCycleAlreadyExists() {
        Setup setup = seedCircleWithParticipants();
        circleFinanceService.createCycle(
                setup.coachId(),
                setup.circle().getId(),
                new CreateCircleCycleRequest(LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("5000"))
        );

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> circleFinanceService.createCycle(
                        setup.coachId(),
                        setup.circle().getId(),
                        new CreateCircleCycleRequest(LocalDate.now().plusDays(1), LocalDate.now().plusDays(31), new BigDecimal("5000"))
                )
        );
        assertEquals("ACTIVE_CYCLE_ALREADY_EXISTS", ex.getMessage());
    }

    @Test
    void createLoan_shouldRequireCoachValidation() {
        Setup setup = seedCircleWithParticipants();
        circleFinanceService.createCycle(
                setup.coachId(),
                setup.circle().getId(),
                new CreateCircleCycleRequest(LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("5000"))
        );

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> circleFinanceService.createLoan(
                        setup.member1Id(),
                        setup.circle().getId(),
                        new CreateCircleLoanRequest(setup.member1Id(), new BigDecimal("100"), new BigDecimal("10"), setup.advisorId())
                )
        );
        assertEquals("COACH_VALIDATION_REQUIRED", ex.getMessage());
    }

    @Test
    void createLoan_shouldRejectWhenBorrowerAlreadyHasActiveLoan() {
        Setup setup = seedCircleWithParticipants();
        circleFinanceService.createCycle(
                setup.coachId(),
                setup.circle().getId(),
                new CreateCircleCycleRequest(LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("5000"))
        );
        circleFinanceService.createLoan(
                setup.coachId(),
                setup.circle().getId(),
                new CreateCircleLoanRequest(setup.member1Id(), new BigDecimal("100"), new BigDecimal("10"), setup.advisorId())
        );

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> circleFinanceService.createLoan(
                        setup.coachId(),
                        setup.circle().getId(),
                        new CreateCircleLoanRequest(setup.member1Id(), new BigDecimal("200"), new BigDecimal("10"), setup.advisorId())
                )
        );
        assertEquals("ACTIVE_LOAN_ALREADY_EXISTS", ex.getMessage());
    }

    @Test
    void createRepayment_shouldMarkLoanAsRepaidWhenAmountReached() {
        Setup setup = seedCircleWithParticipants();
        circleFinanceService.createCycle(
                setup.coachId(),
                setup.circle().getId(),
                new CreateCircleCycleRequest(LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("5000"))
        );
        CircleLoanResponse loan = circleFinanceService.createLoan(
                setup.coachId(),
                setup.circle().getId(),
                new CreateCircleLoanRequest(setup.member1Id(), new BigDecimal("100"), new BigDecimal("10"), setup.advisorId())
        );

        CircleRepaymentResponse first = circleFinanceService.createRepayment(
                setup.member1Id(),
                loan.id(),
                new CreateCircleRepaymentRequest(new BigDecimal("50"), LocalDate.now())
        );
        assertEquals(CircleLoanStatus.DISBURSED, first.loanStatus());

        CircleRepaymentResponse second = circleFinanceService.createRepayment(
                setup.member1Id(),
                loan.id(),
                new CreateCircleRepaymentRequest(new BigDecimal("60"), LocalDate.now())
        );
        assertEquals(CircleLoanStatus.REPAID, second.loanStatus());

        CircleLoan persisted = circleLoanRepository.findById(loan.id()).orElseThrow();
        assertEquals(CircleLoanStatus.REPAID, persisted.getStatus());
    }

    private Setup seedCircleWithParticipants() {
        UUID coachId = createAccountHolder("Coach", "One");
        UUID advisorId = createAccountHolder("Advisor", "One");
        UUID member1Id = createAccountHolder("Member", "One");
        UUID member2Id = createAccountHolder("Member", "Two");
        UUID member3Id = createAccountHolder("Member", "Three");

        Circle circle = circleRepository.save(Circle.builder()
                .name("Finance Circle")
                .coachId(coachId)
                .countryCode("BJ")
                .status(CircleStatus.ACTIVE)
                .build());

        addParticipant(circle, coachId, CircleMemberRole.COACH, false);
        addParticipant(circle, advisorId, CircleMemberRole.ADVISOR_G1, false);
        addParticipant(circle, member1Id, CircleMemberRole.MEMBER, true);
        addParticipant(circle, member2Id, CircleMemberRole.MEMBER, true);
        addParticipant(circle, member3Id, CircleMemberRole.MEMBER, true);

        return new Setup(circle, coachId, advisorId, member1Id);
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

    private void addParticipant(Circle circle, UUID accountHolderId, CircleMemberRole role, boolean financialMember) {
        circleMemberRepository.save(com.ccapp.circles.domain.entity.CircleMember.builder()
                .circle(circle)
                .accountHolderId(accountHolderId)
                .role(role)
                .financialMember(financialMember)
                .joinedAt(OffsetDateTime.now())
                .build());
    }

    private record Setup(Circle circle,
                         UUID coachId,
                         UUID advisorId,
                         UUID member1Id) {
    }
}
