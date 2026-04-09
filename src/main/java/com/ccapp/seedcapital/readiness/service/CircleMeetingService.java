package com.ccapp.seedcapital.readiness.service;

import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleActivityRequestedEvent;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.seedcapital.readiness.domain.entity.CircleMeeting;
import com.ccapp.seedcapital.readiness.domain.entity.CircleMeetingAttendance;
import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingAttendanceStatus;
import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingStatus;
import com.ccapp.seedcapital.readiness.dto.CircleMeetingResponse;
import com.ccapp.seedcapital.readiness.dto.RecordCircleMeetingRequest;
import com.ccapp.seedcapital.readiness.repository.CircleMeetingAttendanceRepository;
import com.ccapp.seedcapital.readiness.repository.CircleMeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CircleMeetingService {

    private final CircleReadinessAccessService circleReadinessAccessService;
    private final CircleMeetingRepository circleMeetingRepository;
    private final CircleMeetingAttendanceRepository circleMeetingAttendanceRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public CircleMeetingResponse recordMeeting(UUID requesterAccountHolderId,
                                               UUID circleId,
                                               RecordCircleMeetingRequest request) {
        circleReadinessAccessService.assertCanManageMeetings(requesterAccountHolderId, circleId);
        List<CircleMember> members = circleReadinessAccessService.requireActiveMembers(circleId);
        CircleMeetingStatus status = request.status() == null ? CircleMeetingStatus.COMPLETED : request.status();
        LinkedHashSet<UUID> presentMemberIds = sanitizePresentMemberIds(request.presentMemberAccountHolderIds());

        validateAttendanceConsistency(status, presentMemberIds);
        validatePresentMembersBelongToCircle(members, presentMemberIds);

        int participantCount = members.size();
        int attendanceCount = status == CircleMeetingStatus.COMPLETED ? presentMemberIds.size() : 0;
        boolean quorumReached = status == CircleMeetingStatus.COMPLETED
                && participantCount > 0
                && attendanceCount >= requiredQuorum(participantCount);

        CircleMeeting meeting = circleMeetingRepository.save(CircleMeeting.builder()
                .circleId(circleId)
                .createdByAccountHolderId(requesterAccountHolderId)
                .meetingAt(request.meetingAt())
                .status(status)
                .participantCount(participantCount)
                .attendanceCount(attendanceCount)
                .quorumReached(quorumReached)
                .notes(normalizeOptionalText(request.notes()))
                .build());

        if (!presentMemberIds.isEmpty()) {
            List<CircleMeetingAttendance> attendanceRows = presentMemberIds.stream()
                    .map(memberId -> CircleMeetingAttendance.builder()
                            .meetingId(meeting.getId())
                            .accountHolderId(memberId)
                            .status(CircleMeetingAttendanceStatus.PRESENT)
                            .build())
                    .toList();
            circleMeetingAttendanceRepository.saveAll(attendanceRows);
        }

        if (status == CircleMeetingStatus.COMPLETED) {
            domainEventPublisher.publish(CircleActivityRequestedEvent.create(
                    circleId,
                    requesterAccountHolderId,
                    ActivityType.MEETING_COMPLETED,
                    meeting.getId(),
                    quorumReached ? "Circle meeting completed with quorum" : "Circle meeting completed without quorum"
            ));
        }

        return toResponse(meeting);
    }

    @Transactional(readOnly = true)
    public List<CircleMeetingResponse> listMeetings(UUID requesterAccountHolderId, UUID circleId) {
        circleReadinessAccessService.assertCanViewReadiness(requesterAccountHolderId, circleId);
        return circleMeetingRepository.findByCircleIdOrderByMeetingAtDesc(circleId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countCompletedMeetingsWithQuorumSince(UUID circleId, OffsetDateTime since) {
        circleReadinessAccessService.requireActiveCircle(circleId);
        return circleMeetingRepository.countByCircleIdAndStatusAndQuorumReachedTrueAndMeetingAtAfter(
                circleId,
                CircleMeetingStatus.COMPLETED,
                since
        );
    }

    private CircleMeetingResponse toResponse(CircleMeeting meeting) {
        List<UUID> presentMemberAccountHolderIds = circleMeetingAttendanceRepository.findByMeetingIdOrderByCreatedAtAsc(meeting.getId())
                .stream()
                .filter(attendance -> attendance.getStatus() == CircleMeetingAttendanceStatus.PRESENT)
                .map(CircleMeetingAttendance::getAccountHolderId)
                .toList();
        return new CircleMeetingResponse(
                meeting.getId(),
                meeting.getCircleId(),
                meeting.getMeetingAt(),
                meeting.getStatus(),
                meeting.getParticipantCount(),
                meeting.getAttendanceCount(),
                meeting.isQuorumReached(),
                meeting.getNotes(),
                meeting.getCreatedByAccountHolderId(),
                presentMemberAccountHolderIds,
                meeting.getCreatedAt(),
                meeting.getUpdatedAt()
        );
    }

    private LinkedHashSet<UUID> sanitizePresentMemberIds(List<UUID> presentMemberAccountHolderIds) {
        if (presentMemberAccountHolderIds == null || presentMemberAccountHolderIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return presentMemberAccountHolderIds.stream()
                .filter(memberId -> memberId != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateAttendanceConsistency(CircleMeetingStatus status, Set<UUID> presentMemberIds) {
        if (status != CircleMeetingStatus.COMPLETED && !presentMemberIds.isEmpty()) {
            throw new BusinessException("Attendance can only be recorded for completed meetings");
        }
    }

    private void validatePresentMembersBelongToCircle(List<CircleMember> members, Set<UUID> presentMemberIds) {
        Set<UUID> memberIds = members.stream()
                .map(CircleMember::getAccountHolderId)
                .collect(Collectors.toSet());
        boolean invalidMemberPresent = presentMemberIds.stream().anyMatch(memberId -> !memberIds.contains(memberId));
        if (invalidMemberPresent) {
            throw new BusinessException("Meeting attendance can only reference current circle members");
        }
    }

    private int requiredQuorum(int participantCount) {
        return (participantCount / 2) + 1;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
