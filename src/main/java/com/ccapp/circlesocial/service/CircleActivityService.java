package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.domain.entity.CircleActivity;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.dto.CircleActivityResponse;
import com.ccapp.circlesocial.repository.CircleActivityRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CircleActivityService {

    private final CircleActivityRepository circleActivityRepository;
    private final CircleRepository circleRepository;
    private final CircleProgressService circleProgressService;
    private final CircleScoreService circleScoreService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public CircleActivityResponse logActivity(UUID circleId,
                                              UUID actorUserId,
                                              ActivityType activityType,
                                              UUID referenceId,
                                              String message) {
        ensureCircleExists(circleId);

        CircleActivity saved = circleActivityRepository.save(CircleActivity.builder()
                .circleId(circleId)
                .actorUserId(actorUserId)
                .activityType(activityType)
                .referenceId(referenceId)
                .message(message)
                .build());

        circleProgressService.recalculateCircleProgress(circleId);
        circleScoreService.recalculateScore(circleId);
        emitCircleActivityNotification(saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CircleActivityResponse> getCircleActivity(UUID circleId) {
        ensureCircleExists(circleId);
        return circleActivityRepository.findByCircleIdOrderByCreatedAtDesc(circleId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void ensureCircleExists(UUID circleId) {
        if (!circleRepository.existsById(circleId)) {
            throw new NotFoundException("Circle not found");
        }
    }

    private CircleActivityResponse toResponse(CircleActivity activity) {
        return new CircleActivityResponse(
                activity.getId(),
                activity.getCircleId(),
                activity.getActorUserId(),
                activity.getActivityType(),
                activity.getReferenceId(),
                activity.getMessage(),
                activity.getCreatedAt()
        );
    }

    private void emitCircleActivityNotification(CircleActivity activity) {
        try {
            // actorUserId stores an account_holder_id — it is the canonical business identity
            // for the circle domain. No app_user lookup needed or valid here.
            UUID recipientAccountHolderId = activity.getActorUserId();

            domainEventPublisher.publish(NotificationRequestedEvent.create(
                    recipientAccountHolderId,
                    recipientAccountHolderId,
                    NotificationType.CIRCLE_ACTIVITY,
                    NotificationChannel.IN_APP,
                    "CIRCLE_ACTIVITY",
                    activity.getMessage(),
                    null,
                    true
            ));
        } catch (RuntimeException ex) {
            log.warn("Failed to emit CIRCLE_ACTIVITY notification for activityId={}", activity.getId(), ex);
        }
    }
}
