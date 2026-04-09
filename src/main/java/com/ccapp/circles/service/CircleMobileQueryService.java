package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.entity.CircleMemberInteraction;
import com.ccapp.circles.domain.enums.CircleMemberInteractionType;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.CircleDetailResponse;
import com.ccapp.circles.dto.CircleListItemResponse;
import com.ccapp.circles.dto.CircleMemberFinancialsResponse;
import com.ccapp.circles.dto.CircleMemberHistoryItemResponse;
import com.ccapp.circles.dto.CircleMemberInteractionResponse;
import com.ccapp.circles.dto.CircleMemberProfileResponse;
import com.ccapp.circles.repository.CircleMemberInteractionRepository;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.circlesocial.domain.entity.CircleActivity;
import com.ccapp.circlesocial.repository.CircleActivityRepository;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.entity.LoanRepayment;
import com.ccapp.credit.domain.entity.RepaymentSchedule;
import com.ccapp.credit.domain.enums.InstallmentStatus;
import com.ccapp.credit.repository.LoanRepaymentRepository;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.repository.RepaymentScheduleRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.chat.service.ChatConversationService;
import com.ccapp.seedcapital.readiness.domain.enums.SeedCapitalCreditBridgeState;
import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingStatus;
import com.ccapp.seedcapital.readiness.repository.CircleMeetingRepository;
import com.ccapp.seedcapital.readiness.service.CircleReadinessAccessService;
import com.ccapp.seedcapital.readiness.service.CircleTrustScoreService;
import com.ccapp.seedcapital.readiness.service.SeedCapitalCreditBridgeService;
import com.ccapp.seedcapital.savings.service.ImfSavingsGateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleMobileQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final CircleActivityRepository circleActivityRepository;
    private final CircleMemberInteractionRepository circleMemberInteractionRepository;
    private final AccountHolderService accountHolderService;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final CircleReadinessAccessService circleReadinessAccessService;
    private final CircleMeetingRepository circleMeetingRepository;
    private final CircleTrustScoreService circleTrustScoreService;
    private final ImfSavingsGateService imfSavingsGateService;
    private final SeedCapitalCreditBridgeService seedCapitalCreditBridgeService;
    private final ChatConversationService chatConversationService;
    private final CircleMeetingPolicyService circleMeetingPolicyService;

    @Transactional(readOnly = true)
    public PageResponse<CircleListItemResponse> listUserCircles(UUID accountHolderId,
                                                                CircleStatus status,
                                                                String search,
                                                                Integer page,
                                                                Integer size) {
        accountHolderService.getById(accountHolderId);
        Pageable pageable = normalizePageable(page, size);
        String normalizedSearch = normalizeSearch(search);
        Page<CircleRepository.CircleListProjection> rawPage = circleRepository.findCircleSummariesForAccountHolder(
                accountHolderId,
                status == null ? null : status.name(),
                normalizedSearch,
                pageable
        );

        Page<CircleListItemResponse> mappedPage = rawPage.map(row -> new CircleListItemResponse(
                UUID.fromString(row.getCircleId()),
                row.getName(),
                CircleStatus.valueOf(row.getStatus()),
                row.getProgress() == null ? BigDecimal.ZERO : row.getProgress(),
                row.getMembersCount(),
                CircleMemberRole.valueOf(row.getRole()),
                row.getUnreadMessages(),
                row.getLastActivity()
        ));
        return PageResponse.from(mappedPage);
    }

    @Transactional
    public CircleDetailResponse getCircleDetail(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        var circle = circleReadinessAccessService.requireActiveCircle(circleId);
        UUID conversationId = chatConversationService.getOrCreateCircleConversationId(circleId);
        int membersCount = Math.toIntExact(circleMemberRepository.countByCircle_Id(circleId));
        OffsetDateTime nextMeetingAt = circleMeetingRepository
                .findFirstByCircleIdAndStatusAndMeetingAtAfterOrderByMeetingAtAsc(
                        circleId,
                        CircleMeetingStatus.SCHEDULED,
                        OffsetDateTime.now()
                )
                .map(meeting -> meeting.getMeetingAt())
                .orElse(null);
        SeedCapitalCreditBridgeState creditCandidateState = circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, requesterAccountHolderId)
                ? seedCapitalCreditBridgeService.getCandidate(requesterAccountHolderId, circleId).state()
                : null;

        return new CircleDetailResponse(
                circle.getId(),
                circle.getName(),
                circle.getStatus(),
                conversationId,
                membersCount,
                nextMeetingAt,
                circleTrustScoreService.getScore(requesterAccountHolderId, circleId).globalScore(),
                imfSavingsGateService.getGate(requesterAccountHolderId, circleId).state(),
                creditCandidateState,
                circleMeetingPolicyService.resolvePolicy(circle)
        );
    }

    @Transactional(readOnly = true)
    public CircleMemberProfileResponse getMemberProfile(UUID requesterAccountHolderId,
                                                        UUID circleId,
                                                        UUID memberAccountHolderId) {
        ensureParticipant(circleId, requesterAccountHolderId);
        CircleMember member = loadCircleMember(circleId, memberAccountHolderId);
        AccountHolder accountHolder = accountHolderService.getById(memberAccountHolderId);

        String profilePicture = appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(memberAccountHolderId)
                .flatMap(appUser -> userProfileRepository.findByUserId(appUser.getId()))
                .map(profile -> profile.getProfilePhotoMediaId() != null ? profile.getProfilePhotoMediaId().toString() : null)
                .orElse(null);

        String status = appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(memberAccountHolderId)
                .flatMap(appUser -> userRepository.findById(appUser.getId()))
                .map(user -> user.getAccountStatus().name())
                .orElse("ACTIVE");

        return new CircleMemberProfileResponse(
                memberAccountHolderId,
                displayName(accountHolder.getFirstName(), accountHolder.getLastName()),
                profilePicture,
                member.getBusinessActivity(),
                member.getJoinedAt(),
                member.getRole(),
                status
        );
    }

    @Transactional(readOnly = true)
    public CircleMemberFinancialsResponse getMemberFinancials(UUID requesterAccountHolderId,
                                                              UUID circleId,
                                                              UUID memberAccountHolderId) {
        ensureParticipant(circleId, requesterAccountHolderId);
        loadCircleMember(circleId, memberAccountHolderId);

        Loan loan = findLatestCanonicalLoan(memberAccountHolderId, circleId);
        if (loan == null) {
            return new CircleMemberFinancialsResponse(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    null,
                    "NO_LOAN"
            );
        }

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoan_IdOrderByInstallmentNumberAsc(loan.getId());
        List<LoanRepayment> repayments = loanRepaymentRepository.findByLoan_IdOrderByPaidAtAsc(loan.getId());

        BigDecimal totalDue = calculateTotalDue(loan, schedules);
        BigDecimal paid = repayments.stream()
                .map(LoanRepayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = totalDue.subtract(paid);
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }

        long installmentsPaid = schedules.stream()
                .filter(schedule -> schedule.getStatus() == InstallmentStatus.PAID)
                .count();

        LocalDate nextInstallmentDate = schedules.stream()
                .filter(schedule -> schedule.getStatus() == InstallmentStatus.PENDING || schedule.getStatus() == InstallmentStatus.LATE)
                .map(RepaymentSchedule::getDueDate)
                .sorted()
                .findFirst()
                .orElse(null);

        return new CircleMemberFinancialsResponse(
                loan.getAmount(),
                remaining,
                installmentsPaid,
                nextInstallmentDate,
                loan.getStatus().name()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<CircleMemberHistoryItemResponse> getMemberHistory(UUID requesterAccountHolderId,
                                                                          UUID circleId,
                                                                          UUID memberAccountHolderId,
                                                                          Integer page,
                                                                          Integer size) {
        ensureParticipant(circleId, requesterAccountHolderId);
        CircleMember member = loadCircleMember(circleId, memberAccountHolderId);

        List<CircleMemberHistoryItemResponse> historyItems = new ArrayList<>();
        for (Loan loan : loanRepository.findByAccountHolderIdAndCircleIdOrderByCreatedAtDesc(memberAccountHolderId, circleId)) {
            historyItems.add(new CircleMemberHistoryItemResponse(
                    "LOAN",
                    "LOAN_SUBMITTED",
                    loan.getStatus().name(),
                    "Loan request for amount " + loan.getAmount(),
                    loan.getAmount(),
                    loan.getCreatedAt(),
                    loan.getId()
            ));

            if (loan.getApprovedAt() != null) {
                historyItems.add(new CircleMemberHistoryItemResponse(
                        "LOAN",
                        "LOAN_APPROVED",
                        loan.getStatus().name(),
                        "Loan approved for amount " + loan.getAmount(),
                        loan.getAmount(),
                        loan.getApprovedAt(),
                        loan.getId()
                ));
            }

            if (loan.getDisbursedAt() != null) {
                historyItems.add(new CircleMemberHistoryItemResponse(
                        "LOAN",
                        "LOAN_DISBURSED",
                        loan.getStatus().name(),
                        "Loan disbursed for amount " + loan.getAmount(),
                        loan.getAmount(),
                        loan.getDisbursedAt(),
                        loan.getId()
                ));
            }

            for (LoanRepayment repayment : loanRepaymentRepository.findByLoan_IdOrderByPaidAtAsc(loan.getId())) {
                historyItems.add(new CircleMemberHistoryItemResponse(
                        "REPAYMENT",
                        "REPAYMENT_RECORDED",
                        "PAID",
                        "Repayment amount " + repayment.getAmount(),
                        repayment.getAmount(),
                        repayment.getPaidAt(),
                        repayment.getId()
                ));
            }
        }

        for (CircleActivity activity : circleActivityRepository.findMemberActivityHistory(circleId, memberAccountHolderId, member.getId())) {
            historyItems.add(new CircleMemberHistoryItemResponse(
                    "PROJECT_PROGRESS",
                    activity.getActivityType().name(),
                    "RECORDED",
                    activity.getMessage(),
                    null,
                    activity.getCreatedAt(),
                    activity.getId()
            ));
        }

        historyItems.sort(Comparator.comparing(CircleMemberHistoryItemResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return paginateInMemory(historyItems, page, size);
    }

    @Transactional
    public CircleMemberInteractionResponse createInteraction(UUID senderAccountHolderId,
                                                             UUID circleId,
                                                             UUID memberAccountHolderId,
                                                             CircleMemberInteractionType type) {
        ensureParticipant(circleId, senderAccountHolderId);
        CircleMember member = loadCircleMember(circleId, memberAccountHolderId);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        if (circleMemberInteractionRepository.existsByCircleIdAndMemberIdAndSenderIdAndCreatedAtBetween(
                circleId,
                memberAccountHolderId,
                senderAccountHolderId,
                startOfDay,
                endOfDay
        )) {
            throw new BusinessException("INTERACTION_DAILY_LIMIT_REACHED");
        }

        CircleMemberInteraction saved = circleMemberInteractionRepository.save(CircleMemberInteraction.builder()
                .circleId(circleId)
                .memberId(member.getAccountHolderId())
                .senderId(senderAccountHolderId)
                .type(type)
                .build());

        return new CircleMemberInteractionResponse(
                saved.getId(),
                saved.getCircleId(),
                saved.getMemberId(),
                saved.getSenderId(),
                saved.getType(),
                saved.getCreatedAt()
        );
    }

    private void ensureParticipant(UUID circleId, UUID accountHolderId) {
        if (!circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, accountHolderId)) {
            throw new NotFoundException("Circle member not found");
        }
    }

    private CircleMember loadCircleMember(UUID circleId, UUID memberAccountHolderId) {
        return circleMemberRepository.findByCircle_IdAndAccountHolderId(circleId, memberAccountHolderId)
                .orElseThrow(() -> new NotFoundException("Circle member not found"));
    }

    private Loan findLatestCanonicalLoan(UUID memberAccountHolderId, UUID circleId) {
        return loanRepository.findByAccountHolderIdAndCircleIdOrderByCreatedAtDesc(memberAccountHolderId, circleId).stream()
                .findFirst()
                .orElse(null);
    }

    private BigDecimal calculateTotalDue(Loan loan, List<RepaymentSchedule> schedules) {
        if (schedules != null && !schedules.isEmpty()) {
            return schedules.stream()
                    .map(RepaymentSchedule::getAmountDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return loan.getAmount().add(
                loan.getAmount()
                        .multiply(loan.getInterestRate())
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );
    }

    private String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? "Unknown" : combined;
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim();
        return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private Pageable normalizePageable(Integer page, Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    private PageResponse<CircleMemberHistoryItemResponse> paginateInMemory(List<CircleMemberHistoryItemResponse> items,
                                                                           Integer page,
                                                                           Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        int start = normalizedPage * normalizedSize;
        int end = Math.min(start + normalizedSize, items.size());
        List<CircleMemberHistoryItemResponse> pageItems = start >= items.size()
                ? List.of()
                : items.subList(start, end);
        int totalPages = items.isEmpty() ? 0 : (int) Math.ceil((double) items.size() / normalizedSize);
        boolean hasNext = end < items.size();
        return new PageResponse<>(pageItems, normalizedPage, normalizedSize, items.size(), totalPages, hasNext);
    }
}
