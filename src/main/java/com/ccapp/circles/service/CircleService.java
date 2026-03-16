package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleActivityRequestedEvent;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.*;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.identity.delegation.service.DelegationService;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.verification.service.PhoneVerificationService;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleService {

    private static final long MAX_FINANCIAL_MEMBERS_PER_CIRCLE = 5;
    private static final long MAX_ASSISTED_MEMBERS_PER_CIRCLE = 2;
    private static final String CROSS_COUNTRY_CIRCLE_NOT_ALLOWED = "CROSS_COUNTRY_CIRCLE_NOT_ALLOWED";
    private static final String TOO_MANY_ASSISTED_MEMBERS = "TOO_MANY_ASSISTED_MEMBERS";
    private static final String MEMBER_NOT_ACTIVE = "MEMBER_NOT_ACTIVE";
    private static final String SPONSOR_NOT_FOUND = "SPONSOR_NOT_FOUND";

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final CountryContextService countryContextService;
    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;
    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final DelegationService delegationService;
    private final DelegatedActionService delegatedActionService;
    private final PhoneVerificationService phoneVerificationService;

    @Value("${circles.assisted-member.require-phone-verification:false}")
    private boolean requirePhoneVerification;

    @Transactional
    public CircleResponse createCircle(UUID accountHolderId, CreateCircleRequest request) {
        ensureCreatorCanCreateCircle(accountHolderId);
        MlmHierarchy hierarchy = resolveMlmHierarchy(accountHolderId);
        String accountHolderCountry = resolveAccountHolderCountry(accountHolderId);
        if (circleRepository.existsActiveCircleForUser(accountHolderId)) {
            throw new ConflictException("User already belongs to an active circle");
        }

        Circle circle = circleRepository.save(Circle.builder()
                .name(request.name().trim())
                .coachId(hierarchy.coachAccountHolderId())
                .countryCode(accountHolderCountry)
                .status(CircleStatus.ACTIVE)
                .build());

        OffsetDateTime now = OffsetDateTime.now();
        List<CircleMember> toInsert = new ArrayList<>();
        Set<UUID> insertedAccountHolders = new HashSet<>();

        addParticipant(toInsert, insertedAccountHolders, circle, hierarchy.coachAccountHolderId(), CircleMemberRole.COACH, false, now);
        addParticipant(toInsert, insertedAccountHolders, circle, hierarchy.advisorGeneration1AccountHolderId(), CircleMemberRole.ADVISOR_G1, false, now);
        addParticipant(toInsert, insertedAccountHolders, circle, hierarchy.advisorGeneration2AccountHolderId(), CircleMemberRole.ADVISOR_G2, false, now);

        if (request.creatorRole() == CircleCreatorRole.MEMBER) {
            addParticipant(toInsert, insertedAccountHolders, circle, accountHolderId, CircleMemberRole.MEMBER, true, now);
        }

        List<CircleMember> savedParticipants = circleMemberRepository.saveAll(toInsert);
        CircleMember activityMember = savedParticipants.stream()
                .filter(member -> Objects.equals(member.getAccountHolderId(), accountHolderId))
                .findFirst()
                .orElseGet(() -> savedParticipants.stream()
                        .filter(member -> member.getRole() == CircleMemberRole.COACH)
                        .findFirst()
                        .orElse(null));

        if (activityMember != null) {
            domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                    circle.getId(),
                    accountHolderId,
                    ActivityType.MEMBER_JOINED,
                    activityMember.getId(),
                    "Circle created with MLM participants"
            ));
        }

        int membersCount = Math.toIntExact(circleMemberRepository.countByCircle_IdAndFinancialMemberTrue(circle.getId()));
        return toCircleResponse(circle, membersCount);
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

        long membersCount = circleMemberRepository.countByCircle_IdAndFinancialMemberTrue(circleId);
        if (membersCount >= MAX_FINANCIAL_MEMBERS_PER_CIRCLE) {
            throw new BusinessException("Circle reached maximum members");
        }

        CircleMember member = circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .accountHolderId(accountHolderId)
                .role(CircleMemberRole.MEMBER)
                .financialMember(true)
                .businessActivity(request.businessActivity())
                .loanStatus(request.loanStatus())
                .repaymentProgress(request.repaymentProgress())
                .joinedAt(OffsetDateTime.now())
                .build());

        domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                circle.getId(),
                accountHolderId,
                ActivityType.MEMBER_JOINED,
                member.getId(),
                "Member joined circle"
        ));
        return toMemberResponse(member);
    }

    @Transactional(readOnly = true)
    public MyCircleResponse getMyCircle(UUID accountHolderId) {
        List<Circle> activeCircles = circleRepository.findActiveCirclesForUser(accountHolderId);
        if (activeCircles.isEmpty()) {
            throw new NotFoundException("No active circle found for user");
        }

        Circle circle = activeCircles.get(0);
        List<CircleMemberResponse> members = circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circle.getId())
                .stream()
                .map(this::toMemberResponse)
                .toList();

        return new MyCircleResponse(
                circle.getId(),
                circle.getName(),
                circle.getCoachId(),
                circle.getStatus(),
                circle.getCreatedAt(),
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
        if (actorUserId == null) {
            throw new BusinessException("Actor user is required");
        }

        Circle circle = circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));
        if (!circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, creatorAccountHolderId)) {
            throw new BusinessException("Creator is not a member of this circle");
        }

        CountryContext creatorContext = countryContextService.resolve(creatorAccountHolderId);
        if (!creatorContext.serviceCountryCode().equalsIgnoreCase(circle.getCountryCode())) {
            throw new BusinessException(CROSS_COUNTRY_CIRCLE_NOT_ALLOWED);
        }

        long assistedCount = circleMemberRepository.countAssistedMembersByCircleId(circleId);
        if (assistedCount >= MAX_ASSISTED_MEMBERS_PER_CIRCLE) {
            throw new BusinessException(TOO_MANY_ASSISTED_MEMBERS);
        }
        long membersCount = circleMemberRepository.countByCircle_IdAndFinancialMemberTrue(circleId);
        if (membersCount >= MAX_FINANCIAL_MEMBERS_PER_CIRCLE) {
            throw new BusinessException("Circle reached maximum members");
        }

        String normalizedPhone = normalizePhone(request.phoneNumber());
        if (request.phoneVerificationId() != null) {
            phoneVerificationService.assertVerifiedForPhone(creatorAccountHolderId, normalizedPhone, request.phoneVerificationId());
        } else if (requirePhoneVerification) {
            throw new BusinessException("PHONE_VERIFICATION_REQUIRED");
        }
        if (accountHolderRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new ConflictException("Phone number already used");
        }

        NameParts nameParts = splitFullName(request.fullName());
        AccountHolder assistedAccountHolder = accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(nameParts.firstName())
                .lastName(nameParts.lastName())
                .phoneNumber(normalizedPhone)
                .countryCode(creatorContext.countryCode())
                .serviceCountryCode(creatorContext.serviceCountryCode())
                .accountType(AccountType.ASSISTED)
                .createdByUserId(actorUserId)
                .createdVia(AccountCreatedVia.CIRCLE_MEMBER)
                .kycStatus(KycStatus.NOT_STARTED)
                .build());

        delegationService.createManagerDelegation(assistedAccountHolder.getId(), actorUserId);

        CircleMember member = circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .accountHolderId(assistedAccountHolder.getId())
                .role(CircleMemberRole.MEMBER)
                .financialMember(true)
                .businessActivity(request.projectDescription().trim())
                .loanStatus("NOT_STARTED")
                .repaymentProgress(null)
                .joinedAt(OffsetDateTime.now())
                .build());

        domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                circle.getId(),
                actorUserId,
                ActivityType.MEMBER_JOINED,
                member.getId(),
                "Assisted member created and joined circle"
        ));
        delegatedActionService.log(
                assistedAccountHolder.getId(),
                actorUserId,
                DelegatedActionType.CIRCLE_ACTIVITY,
                "{\"circleId\":\"" + circle.getId() + "\",\"activityType\":\"" + request.activityType().trim() + "\",\"city\":\"" + request.city().trim() + "\"}"
        );

        return toMemberResponse(member);
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

    private MlmHierarchy resolveMlmHierarchy(UUID accountHolderId) {
        List<MlmRelationship> ancestors = mlmRelationshipRepository.findAncestors(accountHolderId);
        UUID coachAccountHolderId = ancestorForLevel(ancestors, 1)
                .orElseThrow(() -> new BusinessException(SPONSOR_NOT_FOUND));
        UUID advisorGeneration1 = ancestorForLevel(ancestors, 2).orElse(null);
        UUID advisorGeneration2 = ancestorForLevel(ancestors, 3).orElse(null);
        return new MlmHierarchy(coachAccountHolderId, advisorGeneration1, advisorGeneration2);
    }

    private Optional<UUID> ancestorForLevel(List<MlmRelationship> ancestors, int level) {
        return ancestors.stream()
                .filter(ancestor -> ancestor.getLevel() == level)
                .map(MlmRelationship::getAncestorId)
                .findFirst();
    }

    private boolean isActiveUser(User user) {
        return user.getAccountStatus() == AccountStatus.ACTIVE;
    }

    private void addParticipant(List<CircleMember> target,
                                Set<UUID> insertedAccountHolders,
                                Circle circle,
                                UUID accountHolderId,
                                CircleMemberRole role,
                                boolean isFinancialMember,
                                OffsetDateTime joinedAt) {
        if (accountHolderId == null || !insertedAccountHolders.add(accountHolderId)) {
            return;
        }
        target.add(CircleMember.builder()
                .circle(circle)
                .accountHolderId(accountHolderId)
                .role(role)
                .financialMember(isFinancialMember)
                .joinedAt(joinedAt)
                .build());
    }

    private boolean isAdvisorRole(CircleMemberRole role) {
        return role == CircleMemberRole.ADVISOR_G1 || role == CircleMemberRole.ADVISOR_G2;
    }

    private void ensureCircleExists(UUID circleId) {
        if (!circleRepository.existsById(circleId)) {
            throw new NotFoundException("Circle not found");
        }
    }

    private CircleResponse toCircleResponse(Circle circle, int membersCount) {
        return new CircleResponse(
                circle.getId(),
                circle.getName(),
                circle.getCoachId(),
                circle.getStatus(),
                circle.getCreatedAt(),
                membersCount
        );
    }

    private CircleMemberResponse toMemberResponse(CircleMember member) {
        AccountHolder accountHolder = accountHolderRepository.findById(member.getAccountHolderId())
                .orElseThrow(() -> new NotFoundException("Account holder not found"));
        String memberName = displayName(accountHolder.getFirstName(), accountHolder.getLastName());
        String memberType = accountHolder.getAccountType().name();
        String managerName = resolveManagerName(accountHolder);

        return new CircleMemberResponse(
                member.getId(),
                member.getAccountHolderId(),
                member.getRole(),
                memberName,
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

    private String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? "Unknown" : combined;
    }

    private String normalizePhone(String phoneNumber) {
        return phoneNumber.trim();
    }

    private NameParts splitFullName(String fullName) {
        String normalized = Arrays.stream(fullName.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElseThrow(() -> new BusinessException("fullName is required"));
        String[] parts = normalized.split(" ");
        if (parts.length == 1) {
            return new NameParts(parts[0], parts[0]);
        }
        String firstName = parts[0];
        String lastName = normalized.substring(firstName.length()).trim();
        return new NameParts(firstName, lastName);
    }

    private record NameParts(String firstName, String lastName) {
    }

    private record MlmHierarchy(UUID coachAccountHolderId,
                                UUID advisorGeneration1AccountHolderId,
                                UUID advisorGeneration2AccountHolderId) {
    }
}
