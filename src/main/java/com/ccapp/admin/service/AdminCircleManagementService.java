package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminCircleDetailsResponse;
import com.ccapp.admin.dto.AdminCircleMemberResponse;
import com.ccapp.admin.dto.AdminCircleSummaryResponse;
import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCircleManagementService {

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;

    @Transactional(readOnly = true)
    public List<AdminCircleSummaryResponse> listCircles() {
        return circleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCircleDetailsResponse getCircle(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new NotFoundException("Circle not found"));

        List<AdminCircleMemberResponse> members = circleMemberRepository.findByCircle_IdOrderByJoinedAtAsc(circleId)
                .stream()
                .map(this::toMember)
                .toList();

        return new AdminCircleDetailsResponse(
                circle.getId(),
                circle.getName(),
                circle.getCoachId(),
                circle.getStatus(),
                circle.getCreatedAt(),
                members
        );
    }

    @Transactional
    public AdminCircleSummaryResponse suspendCircle(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new NotFoundException("Circle not found"));

        if (circle.getStatus() != CircleStatus.ACTIVE) {
            throw new BusinessException("Only active circles can be suspended");
        }

        circle.setStatus(CircleStatus.CLOSED);
        Circle saved = circleRepository.save(circle);
        return toSummary(saved);
    }

    private AdminCircleSummaryResponse toSummary(Circle circle) {
        long membersCount = circleMemberRepository.countByCircle_Id(circle.getId());
        return new AdminCircleSummaryResponse(
                circle.getId(),
                circle.getName(),
                circle.getCoachId(),
                circle.getStatus(),
                membersCount,
                circle.getCreatedAt()
        );
    }

    private AdminCircleMemberResponse toMember(CircleMember member) {
        return new AdminCircleMemberResponse(
                member.getAccountHolderId(),
                member.getRole(),
                member.getBusinessActivity(),
                member.getLoanStatus(),
                member.getRepaymentProgress(),
                member.getJoinedAt()
        );
    }
}
