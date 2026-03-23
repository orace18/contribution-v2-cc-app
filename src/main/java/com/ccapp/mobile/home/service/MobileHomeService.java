package com.ccapp.mobile.home.service;

import com.ccapp.chat.domain.enums.ChatMessageStatus;
import com.ccapp.chat.repository.ChatMessageRepository;
import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.community.repository.CommunityPostRepository;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.CreditEligibilityRepository;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.mobile.home.dto.MobileHomeCircleSummaryResponse;
import com.ccapp.mobile.home.dto.MobileHomeCommunityHighlightResponse;
import com.ccapp.mobile.home.dto.MobileHomeLoanResponse;
import com.ccapp.mobile.home.dto.MobileHomeMembershipResponse;
import com.ccapp.mobile.home.dto.MobileHomeNotificationItemResponse;
import com.ccapp.mobile.home.dto.MobileHomeNotificationSummaryResponse;
import com.ccapp.mobile.home.dto.MobileHomeResponse;
import com.ccapp.notifications.repository.NotificationRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileHomeService {

    private static final List<LoanStatus> ACTIVE_LOAN_STATUSES = List.of(
            LoanStatus.APPROVED,
            LoanStatus.DISBURSED,
            LoanStatus.ACTIVE
    );

    private static final List<ChatMessageStatus> UNREAD_CHAT_STATUSES = List.of(
            ChatMessageStatus.SENT,
            ChatMessageStatus.DELIVERED
    );

    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final MembershipRepository membershipRepository;
    private final CreditEligibilityRepository creditEligibilityRepository;
    private final LoanRepository loanRepository;
    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CommunityPostRepository communityPostRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public MobileHomeResponse getHome(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);

        MobileHomeMembershipResponse membership = buildMembership(accountHolderId);
        BigDecimal rmsBalance = creditEligibilityRepository.findById(accountHolderId)
                .map(eligibility -> eligibility.getRmsScore() == null ? BigDecimal.ZERO : eligibility.getRmsScore())
                .orElse(BigDecimal.ZERO);

        List<MobileHomeLoanResponse> activeLoans = loanRepository.findByAccountHolderIdAndStatusIn(
                        accountHolderId,
                        ACTIVE_LOAN_STATUSES,
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).stream()
                .map(loan -> new MobileHomeLoanResponse(
                        loan.getId(),
                        loan.getAmount(),
                        loan.getCurrency(),
                        loan.getStatus(),
                        loan.getCreatedAt()
                ))
                .toList();

        MobileHomeCircleSummaryResponse circleSummary = circleRepository.findActiveCirclesForUser(accountHolderId).stream()
                .findFirst()
                .map(this::toCircleSummary)
                .orElse(null);

        List<MobileHomeCommunityHighlightResponse> communityHighlights = communityPostRepository
                .findFeaturedForFeed(PageRequest.of(0, 5)).stream()
                .map(post -> new MobileHomeCommunityHighlightResponse(
                        post.getId(),
                        post.getTitle(),
                        post.getContent(),
                        post.getMediaUrl(),
                        post.getFeaturedAt(),
                        post.getCreatedAt()
                ))
                .toList();

        long unreadMessages = chatMessageRepository.countByRecipientAccountHolderIdAndMessageStatusIn(
                accountHolderId,
                UNREAD_CHAT_STATUSES
        );

        long notificationsTotal = notificationRepository.countByAccountHolderId(accountHolderId);
        List<MobileHomeNotificationItemResponse> latestNotifications = notificationRepository
                .findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId, PageRequest.of(0, 5)).stream()
                .map(notification -> new MobileHomeNotificationItemResponse(
                        notification.getId(),
                        notification.getType(),
                        notification.getMessage(),
                        notification.getStatus(),
                        notification.getCreatedAt()
                ))
                .toList();

        return new MobileHomeResponse(
                membership,
                rmsBalance,
                activeLoans,
                circleSummary,
                communityHighlights,
                unreadMessages,
                new MobileHomeNotificationSummaryResponse(notificationsTotal, latestNotifications)
        );
    }

    private MobileHomeMembershipResponse buildMembership(UUID accountHolderId) {
        Membership latestMembership = membershipRepository
                .findFirstByAccountHolderIdAndExpiresAtIsNotNullOrderByExpiresAtDescCreatedAtDesc(accountHolderId)
                .orElse(null);
        if (latestMembership == null) {
            return new MobileHomeMembershipResponse("NONE", null, null, null);
        }

        OffsetDateTime expiresAt = latestMembership.getExpiresAt();
        boolean active = !expiresAt.isBefore(OffsetDateTime.now());
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(OffsetDateTime.now().toLocalDate(), expiresAt.toLocalDate()));
        return new MobileHomeMembershipResponse(
                active ? "ACTIVE" : "EXPIRED",
                latestMembership.getActivatedAt(),
                expiresAt,
                active ? daysRemaining : 0L
        );
    }

    private MobileHomeCircleSummaryResponse toCircleSummary(Circle circle) {
        long membersCount = circleMemberRepository.countByCircle_IdAndFinancialMemberTrue(circle.getId());
        return new MobileHomeCircleSummaryResponse(
                circle.getId(),
                circle.getName(),
                circle.getStatus(),
                membersCount
        );
    }
}
