package com.ccapp.media.service;

import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.SendMultiChannelNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycNotificationService {

    private static final List<NotificationChannel> DEFAULT_CHANNELS = List.of(
            NotificationChannel.IN_APP,
            NotificationChannel.PUSH
    );

    private final NotificationService notificationService;

    public void notifySubmitted(UUID accountHolderId) {
        send(
                accountHolderId,
                NotificationType.KYC_SUBMITTED,
                "KYC verification started",
                "Your identity verification has started. This usually takes a few minutes."
        );
    }

    public void notifyVerified(UUID accountHolderId) {
        send(
                accountHolderId,
                NotificationType.KYC_VERIFIED,
                "Identity verified",
                "Your identity has been successfully verified. Your account is now activated."
        );
    }

    public void notifyRejected(UUID accountHolderId) {
        send(
                accountHolderId,
                NotificationType.KYC_REJECTED,
                "Verification failed",
                "Your identity verification failed. Please submit a new document."
        );
    }

    public void notifyResubmissionRequired(UUID accountHolderId) {
        send(
                accountHolderId,
                NotificationType.KYC_RESUBMIT_REQUIRED,
                "Additional information required",
                "Some information in your identity verification is incomplete. Please resubmit."
        );
    }

    private void send(UUID accountHolderId, NotificationType type, String title, String message) {
        try {
            notificationService.sendMultiChannel(new SendMultiChannelNotificationRequest(
                    accountHolderId,
                    null,
                    type,
                    DEFAULT_CHANNELS,
                    type.name(),
                    title,
                    message,
                    null,
                    true
            ));
        } catch (RuntimeException ex) {
            log.warn("kyc_notification_failed type={} accountHolderId={}", type, accountHolderId, ex);
        }
    }
}
