package com.ccapp.notifications.scheduler;

import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.SendMultiChannelNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpirationJob {

    private final MembershipRepository membershipRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "${notifications.scheduler.subscription-expiration.cron:0 0 */6 * * *}")
    @Transactional
    public void run() {
        List<Membership> memberships = membershipRepository.findAll(
                Sort.by(Sort.Order.asc("accountHolderId"), Sort.Order.desc("expiresAt"), Sort.Order.desc("createdAt"))
        );

        LocalDate today = LocalDate.now();
        Set<java.util.UUID> processedAccountHolderIds = new HashSet<>();
        for (Membership membership : memberships) {
            if (membership.getAccountHolderId() == null
                    || membership.getExpiresAt() == null
                    || !processedAccountHolderIds.add(membership.getAccountHolderId())) {
                continue;
            }
            LocalDate expirationDate = membership.getExpiresAt().toLocalDate();
            long daysUntilExpiration = ChronoUnit.DAYS.between(today, expirationDate);

            SubscriptionWindow window = resolveWindow(daysUntilExpiration);
            if (window == null) {
                continue;
            }

            if (notificationService.existsNotificationForToday(
                    membership.getAccountHolderId(),
                    window.type(),
                    window.templateCode(),
                    today)) {
                continue;
            }

            try {
                notificationService.sendMultiChannel(new SendMultiChannelNotificationRequest(
                        membership.getAccountHolderId(),
                        membership.getAccountHolderId(),
                        window.type(),
                        List.of(NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.SMS, NotificationChannel.EMAIL),
                        window.templateCode(),
                        window.message(),
                        null,
                        true
                ));
            } catch (RuntimeException ex) {
                log.warn("Subscription expiration notification failed for accountHolderId={}",
                        membership.getAccountHolderId(), ex);
            }
        }
    }

    private SubscriptionWindow resolveWindow(long daysUntilExpiration) {
        if (daysUntilExpiration == 7) {
            return new SubscriptionWindow(NotificationType.SUBSCRIPTION_EXPIRING, "SUBSCRIPTION_EXPIRING_D7", "Your subscription expires in 7 days.");
        }
        if (daysUntilExpiration == 3) {
            return new SubscriptionWindow(NotificationType.SUBSCRIPTION_EXPIRING, "SUBSCRIPTION_EXPIRING_D3", "Your subscription expires in 3 days.");
        }
        if (daysUntilExpiration == 1) {
            return new SubscriptionWindow(NotificationType.SUBSCRIPTION_EXPIRING, "SUBSCRIPTION_EXPIRING_D1", "Your subscription expires in 24 hours.");
        }
        if (daysUntilExpiration == 0) {
            return new SubscriptionWindow(NotificationType.SUBSCRIPTION_EXPIRED, "SUBSCRIPTION_EXPIRED_D0", "Your subscription expires today.");
        }
        if (daysUntilExpiration == -3) {
            return new SubscriptionWindow(NotificationType.SUBSCRIPTION_EXPIRED, "SUBSCRIPTION_EXPIRED_D3", "Your subscription expired 3 days ago.");
        }
        return null;
    }

    private record SubscriptionWindow(NotificationType type, String templateCode, String message) {
    }
}
