package com.ccapp.notifications.service;

import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.repository.UserDeviceRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushService {

    private final ObjectProvider<FirebaseApp> firebaseAppProvider;
    private final UserDeviceRepository userDeviceRepository;
    private final AppUserRepository appUserRepository;

    @Value("${firebase.push.enabled:false}")
    private boolean pushEnabled;

    @Value("${firebase.push.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${firebase.push.retry.initial-delay-ms:500}")
    private long retryInitialDelayMs;

    public String sendToDevice(String token) {
        return sendToDevice(token, "CC App", "", Map.of());
    }

    public String sendToDevice(String token, String title, String body, Map<String, String> data) {
        return enqueueTokens(Set.of(token), title, body, data, "DEVICE", maskToken(token));
    }

    public String sendToUser(UUID userId) {
        return sendToUser(userId, "CC App", "", Map.of());
    }

    public String sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        Set<String> tokens = userDeviceRepository.findByUserIdAndDeletedFalseAndPushTokenIsNotNull(userId).stream()
                .map(UserDevice::getPushToken)
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return enqueueTokens(tokens, title, body, data, "USER", userId == null ? "" : userId.toString());
    }

    public String sendToAccountHolder(UUID accountHolderId, String title, String body, Map<String, String> data) {
        AppUser appUser = appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).orElse(null);
        if (appUser == null) {
            log.info("push_delivery_skipped targetType=ACCOUNT_HOLDER targetId={} reason=no-app-user", accountHolderId);
            return "firebase:no-app-user";
        }
        return sendToUser(appUser.getId(), title, body, data);
    }

    public String broadcast() {
        return broadcast("CC App", "", Map.of());
    }

    public String broadcast(String title, String body, Map<String, String> data) {
        Set<String> tokens = userDeviceRepository.findByDeletedFalseAndPushTokenIsNotNull().stream()
                .map(UserDevice::getPushToken)
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return enqueueTokens(tokens, title, body, data, "BROADCAST", "ALL");
    }

    private String enqueueTokens(Collection<String> tokens,
                                 String title,
                                 String body,
                                 Map<String, String> data,
                                 String targetType,
                                 String targetId) {
        if (!pushEnabled) {
            log.info("push_delivery_skipped targetType={} targetId={} reason=disabled", targetType, targetId);
            return "firebase:disabled";
        }
        FirebaseApp firebaseApp = firebaseAppProvider.getIfAvailable();
        if (firebaseApp == null) {
            log.warn("push_delivery_skipped targetType={} targetId={} reason=no-firebase-app", targetType, targetId);
            return "firebase:unconfigured";
        }

        Set<String> normalizedTokens = tokens.stream()
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (normalizedTokens.isEmpty()) {
            log.info("push_delivery_skipped targetType={} targetId={} reason=no-push-token", targetType, targetId);
            return "firebase:no-targets";
        }

        String dispatchId = UUID.randomUUID().toString();
        CompletableFuture.runAsync(() -> normalizedTokens.forEach(
                token -> sendWithRetry(firebaseApp, dispatchId, targetType, targetId, token, title, body, data)
        ));
        log.info("push_delivery_queued dispatchId={} targetType={} targetId={} tokenCount={}",
                dispatchId, targetType, targetId, normalizedTokens.size());
        return "firebase:queued:" + dispatchId;
    }

    private void sendWithRetry(FirebaseApp firebaseApp,
                               String dispatchId,
                               String targetType,
                               String targetId,
                               String token,
                               String title,
                               String body,
                               Map<String, String> data) {
        long backoffMs = Math.max(1L, retryInitialDelayMs);
        for (int attempt = 1; attempt <= Math.max(1, retryMaxAttempts); attempt++) {
            try {
                Message.Builder builder = Message.builder().setToken(token);
                if (hasText(title) || hasText(body)) {
                    builder.setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(hasText(title) ? title.trim() : "CC App")
                            .setBody(hasText(body) ? body.trim() : "")
                            .build());
                }
                if (data != null && !data.isEmpty()) {
                    builder.putAllData(data);
                }
                String messageId = FirebaseMessaging.getInstance(firebaseApp).send(builder.build());
                log.info("push_delivery_sent dispatchId={} targetType={} targetId={} token={} attempt={} messageId={}",
                        dispatchId, targetType, targetId, maskToken(token), attempt, messageId);
                return;
            } catch (FirebaseMessagingException ex) {
                if (attempt >= Math.max(1, retryMaxAttempts)) {
                    log.warn("push_delivery_failed dispatchId={} targetType={} targetId={} token={} attempt={} reason={}",
                            dispatchId, targetType, targetId, maskToken(token), attempt, ex.getMessage());
                    return;
                }
                log.warn("push_delivery_retry dispatchId={} targetType={} targetId={} token={} attempt={} nextBackoffMs={} reason={}",
                        dispatchId, targetType, targetId, maskToken(token), attempt, backoffMs, ex.getMessage());
            } catch (RuntimeException ex) {
                if (attempt >= Math.max(1, retryMaxAttempts)) {
                    log.warn("push_delivery_failed dispatchId={} targetType={} targetId={} token={} attempt={} reason={}",
                            dispatchId, targetType, targetId, maskToken(token), attempt, ex.getMessage());
                    return;
                }
                log.warn("push_delivery_retry dispatchId={} targetType={} targetId={} token={} attempt={} nextBackoffMs={} reason={}",
                        dispatchId, targetType, targetId, maskToken(token), attempt, backoffMs, ex.getMessage());
            }

            sleep(backoffMs);
            backoffMs = Math.max(backoffMs * 2, backoffMs + 1);
        }
    }

    private void sleep(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Push delivery retry interrupted", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
