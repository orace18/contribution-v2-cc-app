package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.*;
import com.ccapp.circles.repository.*;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    private AccountHolderRepository accountHolderRepository;

    @Test
    void createCycle_shouldBeDisabledForMvp() {
        Setup setup = seedCircleWithParticipants();

        BusinessException ex = assertThrows(BusinessException.class, () -> circleFinanceService.createCycle(
                setup.coachId(),
                setup.circle().getId(),
                new CreateCircleCycleRequest(LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("5000"))
        ));

        assertEquals("CIRCLE_FINANCE_DISABLED_FOR_MVP", ex.getMessage());
    }

    @Test
    void getCycles_shouldReturnEmptyList() {
        Setup setup = seedCircleWithParticipants();
        assertEquals(0, circleFinanceService.getCycles(setup.circle().getId()).size());
    }

    @Test
    void createLoan_shouldBeDisabledForMvp() {
        Setup setup = seedCircleWithParticipants();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> circleFinanceService.createLoan(
                        setup.member1Id(),
                        setup.circle().getId(),
                        new CreateCircleLoanRequest(setup.member1Id(), new BigDecimal("100"), new BigDecimal("10"), setup.advisorId())
                )
        );
        assertEquals("CIRCLE_FINANCE_DISABLED_FOR_MVP", ex.getMessage());
    }

    @Test
    void createRepayment_shouldBeDisabledForMvp() {
        Setup setup = seedCircleWithParticipants();
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> circleFinanceService.createRepayment(
                        setup.member1Id(),
                        UUID.randomUUID(),
                        new CreateCircleRepaymentRequest(new BigDecimal("50"), LocalDate.now())
                )
        );
        assertEquals("CIRCLE_FINANCE_DISABLED_FOR_MVP", ex.getMessage());
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
