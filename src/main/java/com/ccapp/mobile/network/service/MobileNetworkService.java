package com.ccapp.mobile.network.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.projection.MlmTreeMemberProjection;
import com.ccapp.mobile.network.dto.MobileNetworkEligibilityResponse;
import com.ccapp.mobile.onboarding.domain.entity.MobileOnboardingJourney;
import com.ccapp.mobile.onboarding.domain.enums.MobilePlacementStatus;
import com.ccapp.mobile.onboarding.domain.enums.MobileSponsorDecision;
import com.ccapp.mobile.onboarding.repository.MobileOnboardingJourneyRepository;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import com.ccapp.mobile.network.dto.MobileNetworkFollowUpResponse;
import com.ccapp.mobile.network.dto.MobileNetworkMemberSummaryResponse;
import com.ccapp.mobile.network.dto.MobileNetworkMembershipStatus;
import com.ccapp.mobile.network.dto.MobileNetworkMembersResponse;
import com.ccapp.mobile.network.dto.MobileNetworkNextAction;
import com.ccapp.mobile.network.dto.MobileNetworkTeamStatsResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTodayActivityResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTreeNodeResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTreeResponse;
import com.ccapp.mobile.network.dto.MobileNetworkTreeView;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.service.MembershipReconciliationService;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileNetworkService {

    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final AccountHolderRepository accountHolderRepository;
    private final MlmMemberLinkRepository mlmMemberLinkRepository;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MembershipRepository membershipRepository;
    private final MobileOnboardingJourneyRepository onboardingJourneyRepository;
    private final AppUserRepository appUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final MembershipReconciliationService membershipReconciliationService;

    @Value("${mobile.network.zone:Africa/Porto-Novo}")
    private String networkZone;

    @Value("${mobile.network.expiring-window-days:7}")
    private int expiringWindowDays;

    @Value("${mobile.network.follow-up-inactive-days:5}")
    private int followUpInactiveDays;

    @Value("${mobile.network.contact-escalation-days:10}")
    private int contactEscalationDays;

    @Value("${mobile.network.follow-up-limit:20}")
    private int followUpLimit;

    @Value("${mobile.network.search-limit:50}")
    private int searchLimit;

    @Transactional(readOnly = true)
    public MobileNetworkTeamStatsResponse getTeamStats(UUID actorUserId, UUID accountHolderId) {
        assertViewAccess(actorUserId, accountHolderId);
        NetworkContext context = loadContext(accountHolderId, MobileNetworkTreeView.GENEALOGY, false);

        long active = context.lifecycleByMemberId.values().stream()
                .filter(MemberLifecycle::countedAsActive)
                .count();
        long inactive = context.lifecycleByMemberId.values().stream()
                .filter(MemberLifecycle::countedAsInactive)
                .count();
        long toFollowUp = context.lifecycleByMemberId.values().stream()
                .filter(MemberLifecycle::requiresFollowUp)
                .count();

        return new MobileNetworkTeamStatsResponse(active, inactive, toFollowUp);
    }

    @Transactional(readOnly = true)
    public MobileNetworkMembersResponse getDirectMembers(UUID actorUserId, UUID accountHolderId) {
        assertViewAccess(actorUserId, accountHolderId);
        NetworkContext context = loadContext(accountHolderId, MobileNetworkTreeView.GENEALOGY, false);

        List<MobileNetworkMemberSummaryResponse> members = context.rows.stream()
                .filter(row -> row.generation() == 1)
                .map(row -> toMemberSummary(context, row))
                .toList();

        return new MobileNetworkMembersResponse(members);
    }

    @Transactional(readOnly = true)
    public MobileNetworkTodayActivityResponse getTodayActivity(UUID actorUserId, UUID accountHolderId) {
        assertViewAccess(actorUserId, accountHolderId);
        NetworkContext context = loadContext(accountHolderId, MobileNetworkTreeView.GENEALOGY, false);
        OffsetDateTime startOfDay = startOfDay();
        OffsetDateTime endOfDay = startOfDay.plusDays(1);

        long newMembers = context.rows.stream()
                .filter(row -> isWithin(row.linkedAt(), startOfDay, endOfDay))
                .count();

        Set<UUID> paidToday = new HashSet<>();
        for (Map.Entry<UUID, Membership> entry : context.latestMembershipByMemberId.entrySet()) {
            Membership membership = entry.getValue();
            if (membership != null
                    && membership.getStatus() == MembershipStatus.ACTIVE
                    && isWithin(membership.getActivatedAt(), startOfDay, endOfDay)) {
                paidToday.add(entry.getKey());
            }
        }

        return new MobileNetworkTodayActivityResponse(newMembers, paidToday.size());
    }

    /**
     * Returns the direct sponsored members of a specific member within the
     * actor's network. The actor must have VIEW access on their own accountHolderId;
     * the target member must belong to their sponsor tree.
     */
    @Transactional(readOnly = true)
    public MobileNetworkMembersResponse getMemberDirects(UUID actorUserId,
                                                         UUID actorAccountHolderId,
                                                         UUID targetMemberId) {
        assertViewAccess(actorUserId, actorAccountHolderId);
        NetworkContext context = loadContext(targetMemberId, MobileNetworkTreeView.GENEALOGY, false);

        List<MobileNetworkMemberSummaryResponse> members = context.rows.stream()
                .filter(row -> row.generation() == 1)
                .map(row -> toMemberSummary(context, row))
                .toList();

        return new MobileNetworkMembersResponse(members);
    }

    /**
     * Returns sponsor-tree members who have not yet been placed in the binary tree.
     */
    @Transactional(readOnly = true)
    public MobileNetworkMembersResponse getUnplacedMembers(UUID actorUserId, UUID accountHolderId) {
        assertViewAccess(actorUserId, accountHolderId);

        List<MlmTreeMemberProjection> projections = mlmMemberLinkRepository.findUnplacedInSponsorTree(accountHolderId);
        if (projections.isEmpty()) {
            return new MobileNetworkMembersResponse(List.of());
        }

        Set<UUID> memberIds = new HashSet<>();
        projections.forEach(p -> memberIds.add(UUID.fromString(p.getMemberId())));

        Map<UUID, AccountHolder> accountHolderById = loadAccountHolders(memberIds);
        Map<UUID, Membership> latestMembershipByMemberId = loadLatestMemberships(memberIds);
        Map<UUID, String> avatarByMemberId = loadAvatars(memberIds);

        List<MobileNetworkMemberSummaryResponse> members = projections.stream()
                .map(p -> {
                    UUID memberId = UUID.fromString(p.getMemberId());
                    AccountHolder accountHolder = accountHolderById.get(memberId);
                    MemberLifecycle lifecycle = deriveLifecycle(
                            latestMembershipByMemberId.get(memberId),
                            accountHolder,
                            p.getLinkedAt()
                    );
                    return new MobileNetworkMemberSummaryResponse(
                            memberId,
                            accountHolder == null ? null : accountHolder.getFirstName(),
                            accountHolder == null ? null : accountHolder.getLastName(),
                            avatarByMemberId.get(memberId),
                            1,
                            0L,
                            lifecycle.membershipStatus(),
                            lifecycle.nextAction()
                    );
                })
                .toList();

        return new MobileNetworkMembersResponse(members);
    }

    @Transactional(readOnly = true)
    public List<MobileNetworkFollowUpResponse> getFollowUps(UUID actorUserId, UUID accountHolderId) {
        assertViewAccess(actorUserId, accountHolderId);
        NetworkContext context = loadContext(accountHolderId, MobileNetworkTreeView.GENEALOGY, false);

        return context.rows.stream()
                .map(row -> {
                    AccountHolder accountHolder = context.accountHolderById.get(row.memberId());
                    MemberLifecycle lifecycle = context.lifecycleByMemberId.get(row.memberId());
                    if (accountHolder == null || lifecycle == null || !lifecycle.requiresFollowUp()) {
                        return null;
                    }
                    return new MobileNetworkFollowUpResponse(
                            row.memberId(),
                            displayName(accountHolder),
                            lifecycle.inactiveDays(),
                            accountHolder.getPhoneNumber()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(MobileNetworkFollowUpResponse::inactiveDays).reversed()
                        .thenComparing(MobileNetworkFollowUpResponse::name, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, followUpLimit))
                .toList();
    }

    @Transactional(readOnly = true)
    public MobileNetworkTreeResponse getTree(UUID actorUserId,
                                             UUID accountHolderId,
                                             MobileNetworkTreeView view) {
        assertViewAccess(actorUserId, accountHolderId);
        MobileNetworkTreeView resolvedView = view == null ? MobileNetworkTreeView.GENEALOGY : view;
        NetworkContext context = loadContext(accountHolderId, resolvedView, true);

        AccountHolder rootAccountHolder = context.accountHolderById.get(accountHolderId);
        MemberLifecycle rootLifecycle = deriveLifecycle(
                context.latestMembershipByMemberId.get(accountHolderId),
                rootAccountHolder,
                null
        );

        List<MutableTreeNode> nodes = buildTreeNodes(context, accountHolderId);
        sortTree(nodes);

        MobileNetworkTreeNodeResponse root = new MobileNetworkTreeNodeResponse(
                accountHolderId,
                displayName(rootAccountHolder),
                rootAccountHolder == null ? null : rootAccountHolder.getPhoneNumber(),
                context.avatarByMemberId.get(accountHolderId),
                0,
                context.descendantCountByMemberId.getOrDefault(accountHolderId, 0L),
                rootLifecycle.membershipStatus(),
                rootLifecycle.nextAction(),
                nodes.stream().map(MutableTreeNode::toResponse).toList()
        );

        int maxDepth = context.rows.stream()
                .mapToInt(TreeRow::generation)
                .max()
                .orElse(0);

        return new MobileNetworkTreeResponse(
                resolvedView,
                context.rows.size(),
                maxDepth,
                root
        );
    }

    @Transactional(readOnly = true)
    public MobileNetworkMembersResponse search(UUID actorUserId, UUID accountHolderId, String query) {
        assertViewAccess(actorUserId, accountHolderId);
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return new MobileNetworkMembersResponse(List.of());
        }

        NetworkContext context = loadContext(accountHolderId, MobileNetworkTreeView.GENEALOGY, false);

        List<MobileNetworkMemberSummaryResponse> members = context.rows.stream()
                .filter(row -> matchesQuery(context.accountHolderById.get(row.memberId()), normalizedQuery))
                .limit(Math.max(1, searchLimit))
                .map(row -> toMemberSummary(context, row))
                .toList();

        return new MobileNetworkMembersResponse(members);
    }

    @Transactional(readOnly = true)
    public MobileNetworkEligibilityResponse getEligibility(UUID actorUserId, UUID accountHolderId) {
        assertViewAccess(actorUserId, accountHolderId);
        membershipReconciliationService.reconcileAccountHolder(accountHolderId);
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        MobileOnboardingJourney onboardingJourney = onboardingJourneyRepository.findByAccountHolderId(accountHolderId).orElse(null);

        // 1. Sponsor / parent must be a real resolved sponsor, not only a stale
        // onboarding flag. Fall back to the onboarding journey only when the MLM
        // member link is not created yet.
        UUID sponsorAccountHolderId = mlmMemberLinkRepository.findById(accountHolderId)
                .map(link -> link.getSponsorAccountHolderId())
                .or(() -> java.util.Optional.ofNullable(onboardingJourney)
                        .map(MobileOnboardingJourney::getSponsorAccountHolderId))
                .orElse(null);
        boolean hasSponsor = sponsorAccountHolderId != null;
        MobileSponsorDecision sponsorDecision = sponsorDecision(onboardingJourney, sponsorAccountHolderId);
        boolean sponsorResolved = sponsorDecision != MobileSponsorDecision.SPONSOR_PENDING;

        // 2. Phone verified
        boolean phoneVerified = accountHolder.getPhoneVerifiedAt() != null;

        // 3. Paid membership (FREE or absent = not paid)
        Membership membership = membershipRepository
                .findFirstByAccountHolderIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        accountHolderId, MembershipStatus.ACTIVE, OffsetDateTime.now())
                .orElse(null);
        boolean hasPaidMembership = membership != null
                && membership.getPlanGrade() != SubscriptionGrade.FREE;
        String membershipGrade = membership != null
                ? membership.getPlanGrade().name()
                : "NONE";

        // 4. MLM position in tree.
        // A user is considered positioned if they have at least one ancestor at level > 0
        // (placed under a sponsor) OR if they are a declared root node
        // (MlmMemberLink with sponsorAccountHolderId=null and placementAccountHolderId=self,
        //  written when sponsor is explicitly declined).
        boolean hasNetworkPosition = mlmRelationshipRepository
                .countByDescendantIdAndLevelGreaterThan(accountHolderId, 0) > 0
                || mlmMemberLinkRepository.findById(accountHolderId)
                        .map(link -> link.getSponsorAccountHolderId() == null
                                && accountHolderId.equals(link.getPlacementAccountHolderId()))
                        .orElse(false);

        boolean isEligible = sponsorResolved && phoneVerified && hasPaidMembership && hasNetworkPosition;
        MobilePlacementStatus placementStatus = placementStatus(hasPaidMembership, hasNetworkPosition);

        return new MobileNetworkEligibilityResponse(
                hasSponsor,
                sponsorResolved,
                phoneVerified,
                hasPaidMembership,
                membershipGrade,
                hasNetworkPosition,
                isEligible,
                sponsorDecision.name(),
                placementStatus.name()
        );
    }

    private MobileSponsorDecision sponsorDecision(MobileOnboardingJourney onboardingJourney, UUID sponsorAccountHolderId) {
        if (sponsorAccountHolderId != null) {
            return MobileSponsorDecision.SPONSOR_SELECTED;
        }
        if (onboardingJourney == null || !onboardingJourney.isSponsorSet()) {
            return MobileSponsorDecision.SPONSOR_PENDING;
        }
        return MobileSponsorDecision.SPONSOR_DECLINED;
    }

    private MobilePlacementStatus placementStatus(boolean hasPaidMembership, boolean hasNetworkPosition) {
        if (!hasPaidMembership) {
            return MobilePlacementStatus.NOT_APPLICABLE;
        }
        return hasNetworkPosition ? MobilePlacementStatus.PLACED : MobilePlacementStatus.PENDING_PLACEMENT;
    }

    private void assertViewAccess(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
    }

    private NetworkContext loadContext(UUID accountHolderId,
                                       MobileNetworkTreeView view,
                                       boolean includeRoot) {
        List<TreeRow> rows = loadRows(accountHolderId, view);
        Set<UUID> memberIds = new HashSet<>();
        rows.forEach(row -> memberIds.add(row.memberId()));
        if (includeRoot) {
            memberIds.add(accountHolderId);
        }

        Map<UUID, AccountHolder> accountHolderById = loadAccountHolders(memberIds);
        Map<UUID, Membership> latestMembershipByMemberId = loadLatestMemberships(memberIds);
        Map<UUID, String> avatarByMemberId = loadAvatars(memberIds);
        Map<UUID, Long> descendantCountByMemberId = computeDescendantCounts(rows);
        Map<UUID, MemberLifecycle> lifecycleByMemberId = buildLifecycles(rows, accountHolderById, latestMembershipByMemberId);

        if (includeRoot) {
            lifecycleByMemberId.put(
                    accountHolderId,
                    deriveLifecycle(
                            latestMembershipByMemberId.get(accountHolderId),
                            accountHolderById.get(accountHolderId),
                            null
                    )
            );
        }

        return new NetworkContext(
                rows,
                accountHolderById,
                latestMembershipByMemberId,
                avatarByMemberId,
                descendantCountByMemberId,
                lifecycleByMemberId
        );
    }

    private List<TreeRow> loadRows(UUID accountHolderId, MobileNetworkTreeView view) {
        List<MlmTreeMemberProjection> projections = view == MobileNetworkTreeView.PLACEMENT
                ? mlmMemberLinkRepository.findPlacementTree(accountHolderId)
                : mlmMemberLinkRepository.findSponsorTree(accountHolderId);

        List<TreeRow> rows = new ArrayList<>(projections.size());
        for (MlmTreeMemberProjection projection : projections) {
            rows.add(new TreeRow(
                    UUID.fromString(projection.getMemberId()),
                    projection.getParentId() == null ? null : UUID.fromString(projection.getParentId()),
                    projection.getGeneration() == null ? 0 : projection.getGeneration(),
                    projection.getLinkedAt()
            ));
        }
        return rows;
    }

    private Map<UUID, AccountHolder> loadAccountHolders(Collection<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AccountHolder> accountHolderById = new HashMap<>();
        accountHolderRepository.findAllById(memberIds)
                .forEach(accountHolder -> accountHolderById.put(accountHolder.getId(), accountHolder));
        return accountHolderById;
    }

    private Map<UUID, Membership> loadLatestMemberships(Collection<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Membership> latestMembershipByMemberId = new LinkedHashMap<>();
        membershipRepository.findByAccountHolderIdInOrderByAccountHolderIdAscExpiresAtDescCreatedAtDesc(memberIds)
                .forEach(membership -> latestMembershipByMemberId.putIfAbsent(membership.getAccountHolderId(), membership));
        return latestMembershipByMemberId;
    }

    private Map<UUID, String> loadAvatars(Collection<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, AppUser> primaryAppUserByMemberId = new HashMap<>();
        appUserRepository.findByAccountHolderIdIn(memberIds).stream()
                .sorted(Comparator.comparing(AppUser::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(appUser -> primaryAppUserByMemberId.putIfAbsent(appUser.getAccountHolderId(), appUser));

        if (primaryAppUserByMemberId.isEmpty()) {
            return Map.of();
        }

        Set<UUID> userIds = new HashSet<>();
        primaryAppUserByMemberId.values().forEach(appUser -> userIds.add(appUser.getId()));

        Map<UUID, UserProfile> profileByUserId = new HashMap<>();
        userProfileRepository.findByUserIds(userIds)
                .forEach(profile -> profileByUserId.put(profile.getUser().getId(), profile));

        Map<UUID, String> avatarByMemberId = new HashMap<>();
        primaryAppUserByMemberId.forEach((memberId, appUser) -> {
            UserProfile profile = profileByUserId.get(appUser.getId());
            if (profile != null && profile.getProfilePhotoMediaId() != null) {
                avatarByMemberId.put(memberId, profile.getProfilePhotoMediaId().toString());
            }
        });
        return avatarByMemberId;
    }

    private Map<UUID, MemberLifecycle> buildLifecycles(List<TreeRow> rows,
                                                       Map<UUID, AccountHolder> accountHolderById,
                                                       Map<UUID, Membership> latestMembershipByMemberId) {
        Map<UUID, MemberLifecycle> lifecycleByMemberId = new HashMap<>();
        for (TreeRow row : rows) {
            lifecycleByMemberId.put(
                    row.memberId(),
                    deriveLifecycle(
                            latestMembershipByMemberId.get(row.memberId()),
                            accountHolderById.get(row.memberId()),
                            row.linkedAt()
                    )
            );
        }
        return lifecycleByMemberId;
    }

    private Map<UUID, Long> computeDescendantCounts(List<TreeRow> rows) {
        Map<UUID, Long> descendantCountByMemberId = new HashMap<>();
        for (int index = rows.size() - 1; index >= 0; index--) {
            TreeRow row = rows.get(index);
            long existingCount = descendantCountByMemberId.getOrDefault(row.memberId(), 0L);
            if (row.parentId() != null) {
                descendantCountByMemberId.merge(row.parentId(), existingCount + 1, Long::sum);
            }
        }
        return descendantCountByMemberId;
    }

    private MobileNetworkMemberSummaryResponse toMemberSummary(NetworkContext context, TreeRow row) {
        AccountHolder accountHolder = context.accountHolderById.get(row.memberId());
        MemberLifecycle lifecycle = context.lifecycleByMemberId.get(row.memberId());
        return new MobileNetworkMemberSummaryResponse(
                row.memberId(),
                accountHolder == null ? null : accountHolder.getFirstName(),
                accountHolder == null ? null : accountHolder.getLastName(),
                context.avatarByMemberId.get(row.memberId()),
                row.generation(),
                context.descendantCountByMemberId.getOrDefault(row.memberId(), 0L),
                lifecycle == null ? MobileNetworkMembershipStatus.INACTIVE : lifecycle.membershipStatus(),
                lifecycle == null ? MobileNetworkNextAction.REMIND_MEMBER : lifecycle.nextAction()
        );
    }

    private List<MutableTreeNode> buildTreeNodes(NetworkContext context, UUID rootId) {
        Map<UUID, MutableTreeNode> nodeById = new LinkedHashMap<>();
        for (TreeRow row : context.rows) {
            AccountHolder accountHolder = context.accountHolderById.get(row.memberId());
            MemberLifecycle lifecycle = context.lifecycleByMemberId.get(row.memberId());
            nodeById.put(row.memberId(), new MutableTreeNode(
                    row.memberId(),
                    displayName(accountHolder),
                    accountHolder == null ? null : accountHolder.getPhoneNumber(),
                    context.avatarByMemberId.get(row.memberId()),
                    row.generation(),
                    context.descendantCountByMemberId.getOrDefault(row.memberId(), 0L),
                    lifecycle == null ? MobileNetworkMembershipStatus.INACTIVE : lifecycle.membershipStatus(),
                    lifecycle == null ? MobileNetworkNextAction.REMIND_MEMBER : lifecycle.nextAction()
            ));
        }

        List<MutableTreeNode> rootChildren = new ArrayList<>();
        for (TreeRow row : context.rows) {
            MutableTreeNode currentNode = nodeById.get(row.memberId());
            if (currentNode == null) {
                continue;
            }
            if (row.parentId() == null || row.parentId().equals(rootId)) {
                rootChildren.add(currentNode);
                continue;
            }
            MutableTreeNode parentNode = nodeById.get(row.parentId());
            if (parentNode == null) {
                rootChildren.add(currentNode);
                continue;
            }
            parentNode.children().add(currentNode);
        }
        return rootChildren;
    }

    private void sortTree(List<MutableTreeNode> nodes) {
        nodes.sort(Comparator.comparingInt(MutableTreeNode::generation)
                .thenComparing(MutableTreeNode::name, String.CASE_INSENSITIVE_ORDER));
        for (MutableTreeNode node : nodes) {
            sortTree(node.children());
        }
    }

    private MemberLifecycle deriveLifecycle(Membership membership,
                                            AccountHolder accountHolder,
                                            OffsetDateTime linkedAt) {
        OffsetDateTime now = now();
        if (isActiveMembership(membership, now)) {
            OffsetDateTime expiresAt = membership.getExpiresAt();
            if (expiresAt != null && !expiresAt.isAfter(now.plusDays(expiringWindowDays))) {
                return new MemberLifecycle(
                        MobileNetworkMembershipStatus.EXPIRING,
                        MobileNetworkNextAction.VERIFY_CONTRIBUTION,
                        0L,
                        true,
                        false
                );
            }
            return new MemberLifecycle(
                    MobileNetworkMembershipStatus.ACTIVE,
                    MobileNetworkNextAction.CONTACT_MEMBER,
                    0L,
                    true,
                    false
            );
        }

        long inactiveDays = computeInactiveDays(now, membership, accountHolder, linkedAt);
        if (inactiveDays >= followUpInactiveDays) {
            return new MemberLifecycle(
                    MobileNetworkMembershipStatus.TO_FOLLOW_UP,
                    inactiveDays >= contactEscalationDays
                            ? MobileNetworkNextAction.CONTACT_MEMBER
                            : MobileNetworkNextAction.REMIND_MEMBER,
                    inactiveDays,
                    false,
                    true
            );
        }

        return new MemberLifecycle(
                MobileNetworkMembershipStatus.INACTIVE,
                MobileNetworkNextAction.REMIND_MEMBER,
                inactiveDays,
                false,
                false
        );
    }

    private boolean isActiveMembership(Membership membership, OffsetDateTime now) {
        return membership != null
                && membership.getStatus() == MembershipStatus.ACTIVE
                && (membership.getExpiresAt() == null || membership.getExpiresAt().isAfter(now));
    }

    private long computeInactiveDays(OffsetDateTime now,
                                     Membership membership,
                                     AccountHolder accountHolder,
                                     OffsetDateTime linkedAt) {
        OffsetDateTime reference = null;
        if (membership != null) {
            if (membership.getExpiresAt() != null && !membership.getExpiresAt().isAfter(now)) {
                reference = membership.getExpiresAt();
            } else if (membership.getUpdatedAt() != null) {
                reference = membership.getUpdatedAt();
            } else if (membership.getCreatedAt() != null) {
                reference = membership.getCreatedAt();
            }
        }
        if (reference == null) {
            reference = linkedAt != null
                    ? linkedAt
                    : accountHolder == null ? null : accountHolder.getCreatedAt();
        }
        if (reference == null || reference.isAfter(now)) {
            return 0L;
        }
        return Math.max(0L, ChronoUnit.DAYS.between(reference.toLocalDate(), now.toLocalDate()));
    }

    private boolean matchesQuery(AccountHolder accountHolder, String normalizedQuery) {
        if (accountHolder == null) {
            return false;
        }
        String haystack = String.join(
                " ",
                safe(accountHolder.getFirstName()),
                safe(accountHolder.getLastName()),
                safe(accountHolder.getPhoneNumber())
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(normalizedQuery);
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String displayName(AccountHolder accountHolder) {
        if (accountHolder == null) {
            return "Unknown";
        }
        String combined = (safe(accountHolder.getFirstName()) + " " + safe(accountHolder.getLastName())).trim();
        return combined.isEmpty() ? "Unknown" : combined;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneId.of(networkZone));
    }

    private OffsetDateTime startOfDay() {
        OffsetDateTime now = now();
        return now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
    }

    private boolean isWithin(OffsetDateTime candidate, OffsetDateTime startInclusive, OffsetDateTime endExclusive) {
        return candidate != null && !candidate.isBefore(startInclusive) && candidate.isBefore(endExclusive);
    }

    private record TreeRow(
            UUID memberId,
            UUID parentId,
            int generation,
            OffsetDateTime linkedAt
    ) {
    }

    private record MemberLifecycle(
            MobileNetworkMembershipStatus membershipStatus,
            MobileNetworkNextAction nextAction,
            long inactiveDays,
            boolean countedAsActive,
            boolean requiresFollowUp
    ) {
        private boolean countedAsInactive() {
            return !countedAsActive;
        }
    }

    private record NetworkContext(
            List<TreeRow> rows,
            Map<UUID, AccountHolder> accountHolderById,
            Map<UUID, Membership> latestMembershipByMemberId,
            Map<UUID, String> avatarByMemberId,
            Map<UUID, Long> descendantCountByMemberId,
            Map<UUID, MemberLifecycle> lifecycleByMemberId
    ) {
    }

    private record MutableTreeNode(
            UUID accountHolderId,
            String name,
            String phone,
            String avatar,
            int generation,
            long downlineCount,
            MobileNetworkMembershipStatus membershipStatus,
            MobileNetworkNextAction nextAction,
            List<MutableTreeNode> children
    ) {
        private MutableTreeNode(UUID accountHolderId,
                                String name,
                                String phone,
                                String avatar,
                                int generation,
                                long downlineCount,
                                MobileNetworkMembershipStatus membershipStatus,
                                MobileNetworkNextAction nextAction) {
            this(accountHolderId, name, phone, avatar, generation, downlineCount, membershipStatus, nextAction, new ArrayList<>());
        }

        private MobileNetworkTreeNodeResponse toResponse() {
            return new MobileNetworkTreeNodeResponse(
                    accountHolderId,
                    name,
                    phone,
                    avatar,
                    generation,
                    downlineCount,
                    membershipStatus,
                    nextAction,
                    children.stream().map(MutableTreeNode::toResponse).toList()
            );
        }
    }
}
