package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleActivity;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleActivityType;
import com.ccapp.circles.domain.enums.CircleMemberRole;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.*;
import com.ccapp.circles.repository.CircleActivityRepository;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.ConflictException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.repository.UserRepository;
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
    private final CircleActivityRepository circleActivityRepository;
    private final UserRepository userRepository;

    @Transactional
    public CircleResponse createCircle(UUID userId, CreateCircleRequest request) {
        ensureUserExists(userId);
        if (circleRepository.existsActiveCircleForUser(userId)) {
            throw new ConflictException("User already belongs to an active circle");
        }

        Circle circle = circleRepository.save(Circle.builder()
                .name(request.name().trim())
                .coachId(userId)
                .status(CircleStatus.ACTIVE)
                .build());

        circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .userId(userId)
                .role(CircleMemberRole.COACH)
                .joinedAt(OffsetDateTime.now())
                .build());

        logActivity(circle, userId, CircleActivityType.CIRCLE_CREATED, "Circle created");
        return toCircleResponse(circle, 1);
    }

    @Transactional
    public CircleMemberResponse joinCircle(UUID userId, UUID circleId, JoinCircleRequest request) {
        ensureUserExists(userId);
        Circle circle = circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));

        if (circleMemberRepository.existsByCircle_IdAndUserId(circleId, userId)) {
            throw new ConflictException("User already in this circle");
        }
        if (circleRepository.existsActiveCircleForUser(userId)) {
            throw new ConflictException("User already belongs to an active circle");
        }

        long membersCount = circleMemberRepository.countByCircle_Id(circleId);
        if (membersCount >= MAX_MEMBERS_PER_CIRCLE) {
            throw new BusinessException("Circle reached maximum members");
        }

        CircleMember member = circleMemberRepository.save(CircleMember.builder()
                .circle(circle)
                .userId(userId)
                .role(CircleMemberRole.MEMBER)
                .businessActivity(request.businessActivity())
                .loanStatus(request.loanStatus())
                .repaymentProgress(request.repaymentProgress())
                .joinedAt(OffsetDateTime.now())
                .build());

        logActivity(circle, userId, CircleActivityType.MEMBER_JOINED, "Member joined circle");
        return toMemberResponse(member);
    }

    @Transactional(readOnly = true)
    public MyCircleResponse getMyCircle(UUID userId) {
        List<Circle> activeCircles = circleRepository.findActiveCirclesForUser(userId);
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

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
    }

    private void logActivity(Circle circle, UUID actorUserId, CircleActivityType type, String message) {
        circleActivityRepository.save(CircleActivity.builder()
                .circle(circle)
                .actorUserId(actorUserId)
                .activityType(type)
                .message(message)
                .build());
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
                member.getUserId(),
                member.getRole(),
                member.getBusinessActivity(),
                member.getLoanStatus(),
                member.getRepaymentProgress(),
                member.getJoinedAt()
        );
    }
}
