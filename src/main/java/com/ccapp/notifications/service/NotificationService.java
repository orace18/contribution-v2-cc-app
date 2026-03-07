package com.ccapp.notifications.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.notifications.dispatcher.DispatchResult;
import com.ccapp.notifications.dispatcher.NotificationDispatcher;
import com.ccapp.notifications.domain.entity.Notification;
import com.ccapp.notifications.domain.entity.NotificationAttempt;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationStatus;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.dto.NotificationAttemptResponse;
import com.ccapp.notifications.dto.NotificationResponse;
import com.ccapp.notifications.dto.SendMultiChannelNotificationRequest;
import com.ccapp.notifications.repository.NotificationAttemptRepository;
import com.ccapp.notifications.repository.NotificationRepository;
import com.ccapp.notifications.template.NotificationTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationDispatcher notificationDispatcher;
    private final AccountHolderService accountHolderService;
    private final AppUserRepository appUserRepository;
    private final AccessControlService accessControlService;

    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        accountHolderService.getById(request.accountHolderId());
        validateActor(request.actorUserId(), request.accountHolderId());

        NotificationTemplateService.ResolvedMessage resolved = notificationTemplateService.resolve(
                request.channel(),
                request.templateCode(),
                request.message(),
                request.variables()
        );

        Notification notification = notificationRepository.save(Notification.builder()
                .accountHolderId(request.accountHolderId())
                .actorUserId(request.actorUserId())
                .type(request.type())
                .channel(request.channel())
                .templateCode(resolved.templateCode())
                .message(resolved.message())
                .status(NotificationStatus.PENDING)
                .build());

        if (Boolean.TRUE.equals(request.sendNow())) {
            return sendNotification(notification.getId(), request.actorUserId());
        }

        return toResponse(notification, List.of());
    }

    @Transactional
    public NotificationResponse sendNotification(UUID notificationId, UUID actorUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        validateActor(actorUserId, notification.getAccountHolderId());

        if (notification.getStatus() == NotificationStatus.SENT) {
            return toResponse(notification, loadAttempts(notification.getId()));
        }

        int attemptNumber = notificationAttemptRepository.countByNotification_Id(notificationId) + 1;

        DispatchResult dispatchResult;
        NotificationStatus attemptStatus;
        String providerResponse;

        try {
            dispatchResult = notificationDispatcher.dispatch(notification);
            attemptStatus = dispatchResult.success() ? NotificationStatus.SENT : NotificationStatus.FAILED;
            providerResponse = dispatchResult.providerResponse();
        } catch (RuntimeException ex) {
            attemptStatus = NotificationStatus.FAILED;
            providerResponse = ex.getMessage();
        }

        NotificationAttempt attempt = notificationAttemptRepository.save(NotificationAttempt.builder()
                .notification(notification)
                .channel(notification.getChannel())
                .attemptNumber(attemptNumber)
                .status(attemptStatus)
                .providerResponse(providerResponse)
                .build());

        if (attemptStatus == NotificationStatus.SENT) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(OffsetDateTime.now());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
        }
        Notification saved = notificationRepository.save(notification);

        List<NotificationAttempt> attempts = new ArrayList<>();
        attempts.add(attempt);
        attempts.addAll(loadAttempts(saved.getId()).stream()
                .filter(existing -> !existing.getId().equals(attempt.getId()))
                .toList());

        return toResponse(saved, attempts);
    }

    @Transactional
    public NotificationResponse sendNotification(UUID notificationId) {
        return sendNotification(notificationId, null);
    }

    @Transactional
    public List<NotificationResponse> sendMultiChannel(SendMultiChannelNotificationRequest request) {
        accountHolderService.getById(request.accountHolderId());
        validateActor(request.actorUserId(), request.accountHolderId());

        boolean sendNow = request.sendNow() == null || request.sendNow();
        List<NotificationResponse> responses = new ArrayList<>();

        for (NotificationChannel channel : new LinkedHashSet<>(request.channels())) {
            responses.add(createNotification(new CreateNotificationRequest(
                    request.accountHolderId(),
                    request.actorUserId(),
                    request.type(),
                    channel,
                    request.templateCode(),
                    request.message(),
                    request.variables(),
                    sendNow
            )));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForUser(UUID actorUserId, UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);

        if (actorUserId != null) {
            accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
        }

        return notificationRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId).stream()
                .map(notification -> toResponse(notification, loadAttempts(notification.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean existsNotificationForToday(UUID accountHolderId, NotificationType type, String templateCode, LocalDate day) {
        if (templateCode == null || templateCode.isBlank()) {
            return false;
        }
        OffsetDateTime from = day.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);
        return notificationRepository.existsByAccountHolderIdAndTypeAndTemplateCodeAndCreatedAtBetween(
                accountHolderId,
                type,
                templateCode,
                from,
                to
        );
    }

    private void validateActor(UUID actorUserId, UUID accountHolderId) {
        if (actorUserId == null) {
            return;
        }

        if (!appUserRepository.existsById(actorUserId)) {
            throw new NotFoundException("Actor app user not found");
        }

        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);
    }

    private List<NotificationAttempt> loadAttempts(UUID notificationId) {
        return notificationAttemptRepository.findByNotification_IdOrderByCreatedAtDesc(notificationId);
    }

    private NotificationResponse toResponse(Notification notification, List<NotificationAttempt> attempts) {
        List<NotificationAttemptResponse> attemptResponses = attempts.stream()
                .map(attempt -> new NotificationAttemptResponse(
                        attempt.getId(),
                        attempt.getChannel(),
                        attempt.getAttemptNumber(),
                        attempt.getStatus(),
                        attempt.getProviderResponse(),
                        attempt.getCreatedAt()
                ))
                .toList();

        return new NotificationResponse(
                notification.getId(),
                notification.getAccountHolderId(),
                notification.getActorUserId(),
                notification.getType(),
                notification.getChannel(),
                notification.getTemplateCode(),
                notification.getMessage(),
                notification.getStatus(),
                notification.getCreatedAt(),
                notification.getSentAt(),
                attemptResponses
        );
    }
}
