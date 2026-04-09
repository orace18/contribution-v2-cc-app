package com.ccapp.circles.service;

import com.ccapp.chat.service.ChatConversationService;
import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.CircleMemberResponse;
import com.ccapp.circles.dto.CircleParticipantsResponse;
import com.ccapp.circles.dto.CircleResponse;
import com.ccapp.circles.dto.CreateAssistedMemberRequest;
import com.ccapp.circles.dto.CreateCircleRequest;
import com.ccapp.circles.dto.JoinCircleRequest;
import com.ccapp.circles.dto.MyCircleResponse;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleActivityRequestedEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.service.DelegationService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.progress.event.CircleJoinedEvent;
import com.ccapp.seedcapital.readiness.service.CircleCoachAssignmentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleService {

    private static final long DEFAULT_MAX_MEMBERS_PER_CIRCLE = 6;
    private static final long DEFAULT_MAX_ASSISTED_MEMBERS_PER_CIRCLE = 2;
    private static final String CROSS_COUNTRY_CIRCLE_NOT_ALLOWED = "CROSS_COUNTRY_CIRCLE_NOT_ALLOWED";
    private static final String MEMBER_NOT_ACTIVE = "MEMBER_NOT_ACTIVE";
    private static final String ASSISTED_MEMBERS_DISABLED_FOR_MVP = "ASSISTED_MEMBERS_DISABLED_FOR_MVP";
    private static final String CIRCLE_LOCKED_BY_ACTIVE_LOAN = "CIRCLE_LOCKED_BY_ACTIVE_LOAN";
    private static final EnumSet<LoanStatus> CIRCLE_LOCKING_LOAN_STATUSES = EnumSet.of(
            LoanStatus.APPROVED,
            LoanStatus.DISBURSED,
            LoanStatus.ACTIVE
    );

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final CountryContextService countryContextService;
    private final AccountHolderRepository accountHolderRepository;
    private final LoanRepository loanRepository;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final DelegationService delegationService;
    private final CountryConfigResolver countryConfigResolver;
    private final ObjectMapper objectMapper;
    private final ChatConversationService chatConversationService;
    private final UserProfileRepository userProfileRepository;
    private final CircleCoachAssignmentService circleCoachAssignmentService;

    @Transactional
    public CircleResponse createCircle(UUID accountHolderId, CreateCircleRequest request) {
        ensureCreatorCanCreateCircle(accountHolderId);

        String accountHolderCountry = resolveAccountHolderCountry(accountHolderId);
        CircleConfigLimits limits = resolveCircleConfigLimits(accountHolderCountry);
        if (circleRepository.existsActiveCircleForUser(accountHolderId)) {
            throw new ConflictException("User already belongs to an active circle");
        }

        Circle circle = circleRepository.save(Circle.builder()
                .name(request.name().trim())
                // Legacy compatibility only. This stores the circle owner/manager reference
                // until coach assignment moves to a dedicated aggregate.
                .coachId(accountHolderId)
                .countryCode(accountHolderCountry)
                .configSnapshot(buildCircleConfigSnapshot(accountHolderCountry, limits.resolvedConfig()))
                .configVersion(limits.resolvedConfig().version())
                .status(CircleStatus.ACTIVE)
                .build());

        OffsetDateTime now = OffsetDateTime.now();
        // Legacy creatorRole input remains accepted for wire compatibility, but the creator
        // is always treated as a circle member. Circle core no longer assigns a coach here.
        CircleMember creatorMembership = circleMemberRepository.save(buildCircleMember(
                circle,
                accountHolderId,
                CircleMemberRole.MEMBER,
                null,
                now
        ));

        domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                circle.getId(),
                accountHolderId,
                ActivityType.MEMBER_JOINED,
                creatorMembership.getId(),
                "Circle created"
        ));
        domainEventPublisher.publish(CircleJoinedEvent.create(accountHolderId, circle.getId()));

        UUID conversationId = chatConversationService.getOrCreateCircleConversationId(circle.getId());
        int membersCount = Math.toIntExact(circleMemberRepository.countByCircle_Id(circle.getId()));
        return toCircleResponse(circle, conversationId, membersCount);
    }

    @Transactional
    public CircleMemberResponse joinCircle(UUID accountHolderId, UUID circleId, JoinCircleRequest request) {
        String accountHolderCountry = resolveAccountHolderCountry(accountHolderId);
        Circle circle = circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));

        if (!accountHolderCountry.equalsIgnoreCase(circle.getCountryCode())) {
            throw new BusinessException(CROSS_COUNTRY_CIRCLE_NOT_ALLOWED);
        }

        if (circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, accountHolderId)) {
            throw new ConflictException("User already in this circle");
        }
        if (circleRepository.existsActiveCircleForUser(accountHolderId)) {
            throw new ConflictException("User already belongs to an active circle");
        }

        ensureCircleUnlocked(circleId);
        CircleConfigLimits limits = resolveCircleConfigLimits(circle.getCountryCode());
        long membersCount = circleMemberRepository.countByCircle_Id(circleId);
        if (membersCount >= limits.maxMembers()) {
            throw new BusinessException("Circle reached maximum members");
        }

        CircleMember member = circleMemberRepository.save(buildCircleMember(
                circle,
                accountHolderId,
                CircleMemberRole.MEMBER,
                request.businessActivity(),
                OffsetDateTime.now()
        ));

        domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                circle.getId(),
                accountHolderId,
                ActivityType.MEMBER_JOINED,
                member.getId(),
                "Member joined circle"
        ));
        domainEventPublisher.publish(CircleJoinedEvent.create(accountHolderId, circleId));
        return toMemberResponse(member);
    }

    @Transactional
    public void removeMember(UUID requesterAccountHolderId, UUID circleId, UUID targetAccountHolderId) {
        Circle circle = circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));

        if (!Objects.equals(legacyCircleManagerAccountHolderId(circle), requesterAccountHolderId)) {
            throw new BusinessException("Only the circle owner can remove members");
        }
        if (Objects.equals(targetAccountHolderId, requesterAccountHolderId)) {
            throw new BusinessException("Circle owner cannot remove themselves from the circle");
        }

        CircleMember member = circleMemberRepository
                .findByCircle_IdAndAccountHolderId(circleId, targetAccountHolderId)
                .orElseThrow(() -> new NotFoundException("Member not found in this circle"));

        if (loanRepository.existsByAccountHolderIdAndStatusIn(targetAccountHolderId, CIRCLE_LOCKING_LOAN_STATUSES)) {
            throw new BusinessException(CIRCLE_LOCKED_BY_ACTIVE_LOAN);
        }

        circleMemberRepository.delete(member);

        domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                circle.getId(),
                requesterAccountHolderId,
                ActivityType.MEMBER_REMOVED,
                member.getId(),
                "Member removed from circle"
        ));
    }

    @Transactional(readOnly = true, noRollbackFor = NotFoundException.class)
    public MyCircleResponse getMyCircle(UUID accountHolderId) {
        List<Circle> activeCircles = circleRepository.findActiveCirclesForUser(accountHolderId);
        if (activeCircles.isEmpty()) {
            throw new NotFoundException("No active circle found for user");
        }

        Circle circle = activeCircles.get(0);
        UUID conversationId = chatConversationService.getOrCreateCircleConversationId(circle.getId());
        List<CircleMemberResponse> members = circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circle.getId())
                .stream()
                .map(this::toMemberResponse)
                .toList();

        return new MyCircleResponse(
                circle.getId(),
                circle.getName(),
                resolvedCoachAccountHolderId(circle),
                circle.getStatus(),
                circle.getCreatedAt(),
                conversationId,
                members
        );
    }

    @Transactional(readOnly = true)
    public List<CircleMemberResponse> listMembers(UUID circleId) {
        ensureCircleExists(circleId);
        return circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId)
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CircleParticipantsResponse getParticipants(UUID circleId) {
        ensureCircleExists(circleId);
        List<CircleMemberResponse> participants = circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId)
                .stream()
                .map(this::toMemberResponse)
                .toList();

        List<CircleMemberResponse> members = participants.stream()
                .filter(member -> member.role() == CircleMemberRole.MEMBER)
                .toList();
        CircleMemberResponse coach = participants.stream()
                .filter(member -> member.role() == CircleMemberRole.COACH)
                .findFirst()
                .or(() -> resolveAssignedCoachParticipant(circleId, participants))
                .orElse(null);
        List<CircleMemberResponse> advisors = participants.stream()
                .filter(member -> isAdvisorRole(member.role()))
                .sorted(Comparator.comparing(member -> member.role().ordinal()))
                .toList();

        return new CircleParticipantsResponse(members, coach, advisors);
    }

    @Transactional
    public CircleMemberResponse createAssistedMember(UUID creatorAccountHolderId,
                                                     UUID actorUserId,
                                                     UUID circleId,
                                                     CreateAssistedMemberRequest request) {
        throw new BusinessException(ASSISTED_MEMBERS_DISABLED_FOR_MVP);
    }

    private String resolveAccountHolderCountry(UUID accountHolderId) {
        CountryContext context = countryContextService.resolve(accountHolderId);
        return context.serviceCountryCode();
    }

    private void ensureCreatorCanCreateCircle(UUID accountHolderId) {
        boolean activeAppMember = appUserRepository.findByAccountHolderId(accountHolderId).stream()
                .map(AppUser::getId)
                .map(userRepository::findById)
                .flatMap(Optional::stream)
                .anyMatch(this::isActiveUser);
        if (!activeAppMember) {
            throw new BusinessException(MEMBER_NOT_ACTIVE);
        }

        boolean hasActiveMlmMembership = mlmRelationshipRepository.countByDescendantIdAndLevelGreaterThan(accountHolderId, 0) > 0;
        if (!hasActiveMlmMembership) {
            throw new BusinessException(MEMBER_NOT_ACTIVE);
        }
    }

    private boolean isActiveUser(User user) {
        return user.getAccountStatus() == AccountStatus.ACTIVE;
    }

    private CircleMember buildCircleMember(Circle circle,
                                           UUID accountHolderId,
                                           CircleMemberRole role,
                                           String businessActivity,
                                           OffsetDateTime joinedAt) {
        return CircleMember.builder()
                .circle(circle)
                .accountHolderId(accountHolderId)
                .role(role)
                // Legacy compatibility only. Circle core no longer uses this flag as
                // a source of financial truth, but older read paths still depend on it.
                .financialMember(true)
                .businessActivity(normalizeOptionalText(businessActivity))
                .loanStatus(null)
                .repaymentProgress(null)
                .joinedAt(joinedAt)
                .build();
    }

    private boolean isAdvisorRole(CircleMemberRole role) {
        return role == CircleMemberRole.ADVISOR_G1 || role == CircleMemberRole.ADVISOR_G2;
    }

    private void ensureCircleUnlocked(UUID circleId) {
        if (loanRepository.existsByCircleIdAndStatusIn(circleId, CIRCLE_LOCKING_LOAN_STATUSES)) {
            throw new BusinessException(CIRCLE_LOCKED_BY_ACTIVE_LOAN);
        }
    }

    private void ensureCircleExists(UUID circleId) {
        if (!circleRepository.existsById(circleId)) {
            throw new NotFoundException("Circle not found");
        }
    }

    private CircleResponse toCircleResponse(Circle circle, UUID conversationId, int membersCount) {
        return new CircleResponse(
                circle.getId(),
                circle.getName(),
                resolvedCoachAccountHolderId(circle),
                circle.getStatus(),
                circle.getCreatedAt(),
                conversationId,
                membersCount
        );
    }

    private CircleMemberResponse toMemberResponse(CircleMember member) {
        AccountHolder accountHolder = accountHolderRepository.findById(member.getAccountHolderId())
                .orElseThrow(() -> new NotFoundException("Account holder not found"));
        UserProfile profile = userProfileRepository.findByUserId(member.getAccountHolderId()).orElse(null);
        String memberName = resolveDisplayName(accountHolder, profile);
        UUID profilePhotoMediaId = profile != null ? profile.getProfilePhotoMediaId() : null;
        String memberType = accountHolder.getAccountType().name();
        String managerName = resolveManagerName(accountHolder);

        return new CircleMemberResponse(
                member.getId(),
                member.getAccountHolderId(),
                member.getRole(),
                memberName,
                profilePhotoMediaId,
                member.getBusinessActivity(),
                memberType,
                managerName,
                member.getBusinessActivity(),
                member.getLoanStatus(),
                member.getRepaymentProgress(),
                member.getJoinedAt()
        );
    }

    private String resolveManagerName(AccountHolder accountHolder) {
        if (accountHolder.getAccountType() != AccountType.ASSISTED) {
            return null;
        }
        return delegationService.findActiveManagerDelegation(accountHolder.getId())
                .flatMap(delegation -> appUserRepository.findById(delegation.getDelegateUserId()))
                .flatMap(appUser -> accountHolderRepository.findById(appUser.getAccountHolderId()))
                .map(manager -> displayName(manager.getFirstName(), manager.getLastName()))
                .orElse(null);
    }

    private String resolveDisplayName(AccountHolder accountHolder, UserProfile profile) {
        String name = displayName(accountHolder.getFirstName(), accountHolder.getLastName());
        if (name.equals("Unknown") && profile != null) {
            name = displayName(profile.getFirstName(), profile.getLastName());
        }
        return name;
    }

    private String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? "Unknown" : combined;
    }

    private Optional<CircleMemberResponse> resolveAssignedCoachParticipant(UUID circleId,
                                                                           List<CircleMemberResponse> participants) {
        return circleCoachAssignmentService.findAssignedCoachAccountHolderId(circleId)
                .map(assignedCoachId -> participants.stream()
                        .filter(member -> Objects.equals(member.accountHolderId(), assignedCoachId))
                        .findFirst()
                        .map(this::copyAsCoach)
                        .orElseGet(() -> buildSyntheticCoachResponse(assignedCoachId)));
    }

    private CircleMemberResponse copyAsCoach(CircleMemberResponse member) {
        return new CircleMemberResponse(
                member.id(),
                member.accountHolderId(),
                CircleMemberRole.COACH,
                member.name(),
                member.profilePhotoMediaId(),
                member.activity(),
                member.memberType(),
                member.managerName(),
                member.businessActivity(),
                member.loanStatus(),
                member.repaymentProgress(),
                member.joinedAt()
        );
    }

    private CircleMemberResponse buildSyntheticCoachResponse(UUID coachAccountHolderId) {
        AccountHolder accountHolder = accountHolderRepository.findById(coachAccountHolderId)
                .orElseThrow(() -> new NotFoundException("Coach account holder not found"));
        UserProfile profile = userProfileRepository.findByUserId(coachAccountHolderId).orElse(null);
        String coachName = resolveDisplayName(accountHolder, profile);
        UUID profilePhotoMediaId = profile != null ? profile.getProfilePhotoMediaId() : null;
        return new CircleMemberResponse(
                null,
                coachAccountHolderId,
                CircleMemberRole.COACH,
                coachName,
                profilePhotoMediaId,
                null,
                accountHolder.getAccountType().name(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private UUID legacyCircleManagerAccountHolderId(Circle circle) {
        return circle.getCoachId();
    }

    private UUID resolvedCoachAccountHolderId(Circle circle) {
        return circleCoachAssignmentService.findAssignedCoachAccountHolderId(circle.getId())
                .orElseGet(() -> legacyCircleManagerAccountHolderId(circle));
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private CircleConfigLimits resolveCircleConfigLimits(String countryCode) {
        CountryConfigResolver.ResolvedConfig resolved = countryConfigResolver.resolveActiveConfig(
                countryCode,
                CountryConfigType.GENERAL
        );
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(resolved);
        ObjectNode circlePayload = payload.has("circle") && payload.get("circle").isObject()
                ? (ObjectNode) payload.get("circle")
                : payload;
        long maxMembers = longField(circlePayload, "maxMembers", DEFAULT_MAX_MEMBERS_PER_CIRCLE);
        long maxAssistedMembers = longField(circlePayload, "maxAssistedMembers", DEFAULT_MAX_ASSISTED_MEMBERS_PER_CIRCLE);
        return new CircleConfigLimits(maxMembers, maxAssistedMembers, resolved);
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

    private String buildCircleConfigSnapshot(String countryCode, CountryConfigResolver.ResolvedConfig circleConfig) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("countryCode", countryCode);
        root.put("circleConfigVersion", circleConfig.version());
        root.set("circle", countryConfigResolver.payloadAsObjectNode(circleConfig));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Unable to serialize circle configuration snapshot");
        }
    }

    private record CircleConfigLimits(long maxMembers,
                                      long maxAssistedMembers,
                                      CountryConfigResolver.ResolvedConfig resolvedConfig) {
    }
}
