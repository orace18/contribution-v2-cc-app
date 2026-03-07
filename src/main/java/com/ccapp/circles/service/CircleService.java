package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.country.service.CountryService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.service.CircleActivityService;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.*;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleService {

    private static final long MAX_MEMBERS_PER_CIRCLE = 5;

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CircleActivityService circleActivityService;
    private final AccountHolderService accountHolderService;
    private final CountryService countryService;

    @Transactional
    public CircleResponse createCircle(UUID accountHolderId, CreateCircleRequest request) {
        String accountHolderCountry = resolveAccountHolderCountry(accountHolderId);
        if (circleRepository.existsActiveCircleForUser(accountHolderId)) {
            throw new ConflictException("User already belongs to an active circle");
        }

        Circle circle = circleRepository.save(Circle.builder()
                .name(request.name().trim())
                .coachId(accountHolderId)
                .countryCode(accountHolderCountry)
                .status(CircleStatus.ACTIVE)
                .build());

        CircleMember coachMember = circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .accountHolderId(accountHolderId)
                .role(CircleMemberRole.COACH)
                .joinedAt(OffsetDateTime.now())
                .build());

        circleActivityService.logActivity(
                circle.getId(),
                accountHolderId,
                ActivityType.MEMBER_JOINED,
                coachMember.getId(),
                "Coach created and joined circle"
        );
        return toCircleResponse(circle, 1);
    }

    @Transactional
    public CircleMemberResponse joinCircle(UUID accountHolderId, UUID circleId, JoinCircleRequest request) {
        String accountHolderCountry = resolveAccountHolderCountry(accountHolderId);
        Circle circle = circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));

        if (!accountHolderCountry.equalsIgnoreCase(circle.getCountryCode())) {
            throw new BusinessException("Member country must match circle country");
        }

        if (circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, accountHolderId)) {
            throw new ConflictException("User already in this circle");
        }
        if (circleRepository.existsActiveCircleForUser(accountHolderId)) {
            throw new ConflictException("User already belongs to an active circle");
        }

        long membersCount = circleMemberRepository.countByCircle_Id(circleId);
        if (membersCount >= MAX_MEMBERS_PER_CIRCLE) {
            throw new BusinessException("Circle reached maximum members");
        }

        CircleMember member = circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .accountHolderId(accountHolderId)
                .role(CircleMemberRole.MEMBER)
                .businessActivity(request.businessActivity())
                .loanStatus(request.loanStatus())
                .repaymentProgress(request.repaymentProgress())
                .joinedAt(OffsetDateTime.now())
                .build());

        circleActivityService.logActivity(
                circle.getId(),
                accountHolderId,
                ActivityType.MEMBER_JOINED,
                member.getId(),
                "Member joined circle"
        );
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
        if (!circleRepository.existsById(circleId)) {
            throw new NotFoundException("Circle not found");
        }
        return circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId)
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    private String resolveAccountHolderCountry(UUID accountHolderId) {
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        return countryService.resolveByCodeOrThrow(accountHolder.getCountryCode()).getCode();
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
        return new CircleMemberResponse(
                member.getId(),
                member.getAccountHolderId(),
                member.getRole(),
                member.getBusinessActivity(),
                member.getLoanStatus(),
                member.getRepaymentProgress(),
                member.getJoinedAt()
        );
    }
}
