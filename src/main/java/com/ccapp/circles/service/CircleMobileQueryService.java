package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.CircleContribution;
import com.ccapp.circles.domain.entity.CircleLoan;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.entity.CircleMemberInteraction;
import com.ccapp.circles.domain.entity.CircleRepayment;
import com.ccapp.circles.domain.enums.CircleMemberInteractionType;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleRepaymentStatus;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.CircleListItemResponse;
import com.ccapp.circles.dto.CircleMemberFinancialsResponse;
import com.ccapp.circles.dto.CircleMemberHistoryItemResponse;
import com.ccapp.circles.dto.CircleMemberInteractionResponse;
import com.ccapp.circles.dto.CircleMemberProfileResponse;
import com.ccapp.circles.repository.CircleContributionRepository;
import com.ccapp.circles.repository.CircleLoanRepository;
import com.ccapp.circles.repository.CircleMemberInteractionRepository;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepaymentRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.circlesocial.domain.entity.CircleActivity;
import com.ccapp.circlesocial.repository.CircleActivityRepository;
import com.ccapp.common.api.PageResponse;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final CircleLoanRepository circleLoanRepository;
    private final CircleRepaymentRepository circleRepaymentRepository;
    private final CircleContributionRepository circleContributionRepository;
    private final CircleActivityRepository circleActivityRepository;
    private final CircleMemberInteractionRepository circleMemberInteractionRepository;
    private final AccountHolderService accountHolderService;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

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
        CircleMember member = loadCircleMember(circleId, memberAccountHolderId);

        CircleLoan loan = circleLoanRepository.findLatestMemberLoanByCircle(circleId, member.getId()).orElse(null);
        if (loan == null) {
            return new CircleMemberFinancialsResponse(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    null,
                    "NO_LOAN"
            );
        }

        List<CircleRepayment> repayments = circleRepaymentRepository.findByLoan_IdOrderByDueDateAsc(loan.getId());
        BigDecimal paid = repayments.stream()
                .filter(repayment -> repayment.getStatus() == CircleRepaymentStatus.PAID || repayment.getStatus() == CircleRepaymentStatus.LATE)
                .map(CircleRepayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long installmentsPaid = repayments.stream()
                .filter(repayment -> repayment.getStatus() == CircleRepaymentStatus.PAID || repayment.getStatus() == CircleRepaymentStatus.LATE)
                .count();

        LocalDate nextInstallmentDate = repayments.stream()
                .filter(repayment -> repayment.getStatus() == CircleRepaymentStatus.PENDING)
                .map(CircleRepayment::getDueDate)
                .sorted()
                .findFirst()
                .orElse(null);

        BigDecimal expected = loan.getAmount().add(loan.getAmount()
                .multiply(loan.getInterest())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal remaining = expected.subtract(paid);
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }

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
        for (CircleContribution contribution : circleContributionRepository.findByCycle_Circle_IdAndMember_IdOrderByDueDateDesc(circleId, member.getId())) {
            OffsetDateTime occurredAt = contribution.getPaidAt() != null
                    ? contribution.getPaidAt()
                    : OffsetDateTime.of(contribution.getDueDate(), LocalTime.MIDNIGHT, ZoneOffset.UTC);
            historyItems.add(new CircleMemberHistoryItemResponse(
                    "CONTRIBUTION",
                    "CONTRIBUTION_" + contribution.getStatus().name(),
                    contribution.getStatus().name(),
                    "Contribution amount " + contribution.getAmount(),
                    contribution.getAmount(),
                    occurredAt,
                    contribution.getId()
            ));
        }

        List<CircleLoan> loans = circleLoanRepository.findMemberLoansByCircleOrderByTimelineDesc(circleId, member.getId());
        for (CircleLoan loan : loans) {
            OffsetDateTime occurredAt = loan.getDisbursedAt() != null ? loan.getDisbursedAt() : loan.getApprovedAt();
            if (occurredAt != null) {
                historyItems.add(new CircleMemberHistoryItemResponse(
                        "LOAN",
                        "LOAN_" + loan.getStatus().name(),
                        loan.getStatus().name(),
                        "Loan event for amount " + loan.getAmount(),
                        loan.getAmount(),
                        occurredAt,
                        loan.getId()
                ));
            }
            for (CircleRepayment repayment : circleRepaymentRepository.findByLoan_IdOrderByDueDateAsc(loan.getId())) {
                OffsetDateTime repaymentAt = repayment.getPaidAt() != null
                        ? repayment.getPaidAt()
                        : OffsetDateTime.of(repayment.getDueDate(), LocalTime.MIDNIGHT, ZoneOffset.UTC);
                historyItems.add(new CircleMemberHistoryItemResponse(
                        "REPAYMENT",
                        "REPAYMENT_" + repayment.getStatus().name(),
                        repayment.getStatus().name(),
                        "Repayment amount " + repayment.getAmount(),
                        repayment.getAmount(),
                        repaymentAt,
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
