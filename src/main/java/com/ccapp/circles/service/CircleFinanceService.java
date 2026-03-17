package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.*;
import com.ccapp.circles.domain.enums.*;
import com.ccapp.circles.dto.*;
import com.ccapp.circles.repository.*;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleFinanceService {

    private static final long DEFAULT_MIN_FINANCIAL_MEMBERS = 3;
    private static final long DEFAULT_MAX_FINANCIAL_MEMBERS = 5;
    private static final Set<CircleLoanStatus> ACTIVE_LOAN_STATUSES = EnumSet.of(
            CircleLoanStatus.REQUESTED,
            CircleLoanStatus.APPROVED,
            CircleLoanStatus.DISBURSED
    );

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CircleCycleRepository circleCycleRepository;
    private final CircleContributionRepository circleContributionRepository;
    private final CircleLoanRepository circleLoanRepository;
    private final CircleRepaymentRepository circleRepaymentRepository;
    private final CountryConfigResolver countryConfigResolver;

    @Transactional
    public CircleCycleResponse createCycle(UUID actorAccountHolderId, UUID circleId, CreateCircleCycleRequest request) {
        Circle circle = circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));
        if (!circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, actorAccountHolderId)) {
            throw new BusinessException("NOT_CIRCLE_PARTICIPANT");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException("INVALID_CYCLE_DATES");
        }
        ensureFinancialMembersCountValid(circle);
        if (circleCycleRepository.existsByCircle_IdAndStatus(circleId, CircleCycleStatus.ACTIVE)) {
            throw new BusinessException("ACTIVE_CYCLE_ALREADY_EXISTS");
        }

        int nextCycleNumber = circleCycleRepository.findTopByCircle_IdOrderByCycleNumberDesc(circleId)
                .map(existing -> existing.getCycleNumber() + 1)
                .orElse(1);

        CircleCycle cycle = circleCycleRepository.save(CircleCycle.builder()
                .circle(circle)
                .cycleNumber(nextCycleNumber)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(CircleCycleStatus.ACTIVE)
                .build());

        List<CircleMember> financialMembers = circleMemberRepository.findByCircle_IdAndFinancialMemberTrueOrderByJoinedAtAsc(circleId);
        List<CircleContribution> contributions = financialMembers.stream()
                .map(member -> CircleContribution.builder()
                        .cycle(cycle)
                        .member(member)
                        .amount(request.contributionAmount())
                        .status(CircleContributionStatus.PENDING)
                        .dueDate(request.endDate())
                        .build())
                .toList();
        circleContributionRepository.saveAll(contributions);

        return toCycleResponse(cycle);
    }

    @Transactional(readOnly = true)
    public List<CircleCycleResponse> getCycles(UUID circleId) {
        if (!circleRepository.existsById(circleId)) {
            throw new NotFoundException("Circle not found");
        }
        return circleCycleRepository.findByCircle_IdOrderByCycleNumberDesc(circleId).stream()
                .map(this::toCycleResponse)
                .toList();
    }

    @Transactional
    public CircleLoanResponse createLoan(UUID actorAccountHolderId, UUID circleId, CreateCircleLoanRequest request) {
        Circle circle = circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));
        if (!circle.getCoachId().equals(actorAccountHolderId)) {
            throw new BusinessException("COACH_VALIDATION_REQUIRED");
        }
        ensureFinancialMembersCountValid(circle);

        CircleCycle activeCycle = circleCycleRepository.findByCircle_IdAndStatus(circleId, CircleCycleStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("ACTIVE_CYCLE_NOT_FOUND"));
        CircleMember borrower = circleMemberRepository.findByCircle_IdAndAccountHolderId(circleId, request.borrowerAccountHolderId())
                .orElseThrow(() -> new BusinessException("BORROWER_NOT_IN_CIRCLE"));
        if (!borrower.isFinancialMember()) {
            throw new BusinessException("ONLY_FINANCIAL_MEMBERS_CAN_BORROW");
        }
        if (circleLoanRepository.existsByBorrower_IdAndStatusIn(borrower.getId(), ACTIVE_LOAN_STATUSES)) {
            throw new BusinessException("ACTIVE_LOAN_ALREADY_EXISTS");
        }

        if (request.advisorAccountHolderId() != null) {
            CircleMember advisor = circleMemberRepository.findByCircle_IdAndAccountHolderId(circleId, request.advisorAccountHolderId())
                    .orElseThrow(() -> new BusinessException("ADVISOR_NOT_IN_CIRCLE"));
            if (!(advisor.getRole() == CircleMemberRole.ADVISOR_G1 || advisor.getRole() == CircleMemberRole.ADVISOR_G2)) {
                throw new BusinessException("ADVISOR_PARTICIPATION_INVALID");
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        CircleLoan loan = circleLoanRepository.save(CircleLoan.builder()
                .cycle(activeCycle)
                .borrower(borrower)
                .amount(request.amount())
                .interest(request.interest())
                .status(CircleLoanStatus.DISBURSED)
                .approvedAt(now)
                .disbursedAt(now)
                .build());

        return toLoanResponse(loan);
    }

    @Transactional
    public CircleRepaymentResponse createRepayment(UUID actorAccountHolderId, UUID loanId, CreateCircleRepaymentRequest request) {
        CircleLoan loan = circleLoanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Circle loan not found"));
        if (!loan.getBorrower().getAccountHolderId().equals(actorAccountHolderId)) {
            throw new BusinessException("ONLY_BORROWER_CAN_REPAY");
        }
        if (loan.getStatus() != CircleLoanStatus.DISBURSED && loan.getStatus() != CircleLoanStatus.APPROVED) {
            throw new BusinessException("LOAN_NOT_ACTIVE");
        }

        OffsetDateTime now = OffsetDateTime.now();
        CircleRepaymentStatus repaymentStatus = now.toLocalDate().isAfter(request.dueDate())
                ? CircleRepaymentStatus.LATE
                : CircleRepaymentStatus.PAID;

        CircleRepayment repayment = circleRepaymentRepository.save(CircleRepayment.builder()
                .loan(loan)
                .amount(request.amount())
                .dueDate(request.dueDate())
                .paidAt(now)
                .status(repaymentStatus)
                .build());

        BigDecimal totalPaid = circleRepaymentRepository.sumByLoanIdAndStatus(loanId, CircleRepaymentStatus.PAID)
                .add(circleRepaymentRepository.sumByLoanIdAndStatus(loanId, CircleRepaymentStatus.LATE));
        BigDecimal totalDue = loan.getAmount().add(
                loan.getAmount()
                        .multiply(loan.getInterest())
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );
        if (totalPaid.compareTo(totalDue) >= 0) {
            loan.setStatus(CircleLoanStatus.REPAID);
            circleLoanRepository.save(loan);
        }

        return toRepaymentResponse(repayment, loan.getStatus());
    }

    private void ensureFinancialMembersCountValid(Circle circle) {
        CircleMemberBounds bounds = resolveCircleMemberBounds(circle.getCountryCode());
        long financialMembersCount = circleMemberRepository.countByCircle_IdAndFinancialMemberTrue(circle.getId());
        if (financialMembersCount < bounds.minFinancialMembers() || financialMembersCount > bounds.maxFinancialMembers()) {
            throw new BusinessException("CIRCLE_MEMBERS_COUNT_INVALID");
        }
    }

    private CircleMemberBounds resolveCircleMemberBounds(String countryCode) {
        CountryConfigResolver.ResolvedConfig config = countryConfigResolver.resolveActiveConfig(
                countryCode,
                CountryConfigType.CIRCLE_CONFIG
        );
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(config);
        long min = longField(payload, "minMembers", DEFAULT_MIN_FINANCIAL_MEMBERS);
        long max = longField(payload, "maxMembers", DEFAULT_MAX_FINANCIAL_MEMBERS);
        if (max < min) {
            throw new BusinessException("circle_config.maxMembers must be >= minMembers");
        }
        return new CircleMemberBounds(min, max);
    }

    private long longField(ObjectNode payload, String fieldName, long defaultValue) {
        if (payload == null || !payload.has(fieldName) || payload.get(fieldName).isNull()) {
            return defaultValue;
        }
        if (!payload.get(fieldName).canConvertToLong()) {
            throw new BusinessException(fieldName + " must be an integer");
        }
        long value = payload.get(fieldName).longValue();
        return value > 0 ? value : defaultValue;
    }

    private CircleCycleResponse toCycleResponse(CircleCycle cycle) {
        return new CircleCycleResponse(
                cycle.getId(),
                cycle.getCircle().getId(),
                cycle.getCycleNumber(),
                cycle.getStartDate(),
                cycle.getEndDate(),
                cycle.getStatus(),
                cycle.getCreatedAt()
        );
    }

    private CircleLoanResponse toLoanResponse(CircleLoan loan) {
        return new CircleLoanResponse(
                loan.getId(),
                loan.getCycle().getId(),
                loan.getBorrower().getAccountHolderId(),
                loan.getAmount(),
                loan.getInterest(),
                loan.getStatus(),
                loan.getApprovedAt(),
                loan.getDisbursedAt()
        );
    }

    private CircleRepaymentResponse toRepaymentResponse(CircleRepayment repayment, CircleLoanStatus loanStatus) {
        return new CircleRepaymentResponse(
                repayment.getId(),
                repayment.getLoan().getId(),
                repayment.getAmount(),
                repayment.getDueDate(),
                repayment.getPaidAt(),
                repayment.getStatus(),
                loanStatus
        );
    }

    private record CircleMemberBounds(long minFinancialMembers, long maxFinancialMembers) {
    }
}
