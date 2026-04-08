package com.ccapp.prayer.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.NotificationDeepLinkRequest;
import com.ccapp.notifications.event.NotificationRequestedEvent;
import com.ccapp.prayer.domain.entity.PrayerDailyContent;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.enums.PrayerDeepLinkRouteKey;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerNotificationPublisher {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final DomainEventPublisher domainEventPublisher;
    private final AccountHolderRepository accountHolderRepository;
    private final ObjectMapper objectMapper;

    public void publishSupportReceived(UUID actorAccountHolderId, String actorDisplayName, PrayerPost post) {
        try {
            if (post.getAuthorAccountHolderId() == null || post.getAuthorAccountHolderId().equals(actorAccountHolderId)) {
                return;
            }
            publishNotification(
                    post.getAuthorAccountHolderId(),
                    NotificationType.PRAYER_SUPPORT_RECEIVED,
                    "PRAYER_SUPPORT_RECEIVED",
                    Map.of("actorDisplayName", normalizeDisplayName(actorDisplayName)),
                    post.getId().toString(),
                    buildFeedDeepLink(post.getId(), "Voir la demande")
            );
        } catch (RuntimeException ex) {
            log.warn("Prayer support notification publish failed postId={} actorAccountHolderId={}",
                    post.getId(), actorAccountHolderId, ex);
        }
    }

    public void publishCommentReceived(UUID actorAccountHolderId, String actorDisplayName, PrayerPost post) {
        try {
            if (post.getAuthorAccountHolderId() == null || post.getAuthorAccountHolderId().equals(actorAccountHolderId)) {
                return;
            }
            publishNotification(
                    post.getAuthorAccountHolderId(),
                    NotificationType.PRAYER_COMMENT_RECEIVED,
                    "PRAYER_COMMENT_RECEIVED",
                    Map.of("actorDisplayName", normalizeDisplayName(actorDisplayName)),
                    post.getId().toString(),
                    buildFeedDeepLink(post.getId(), "Voir le commentaire")
            );
        } catch (RuntimeException ex) {
            log.warn("Prayer comment notification publish failed postId={} actorAccountHolderId={}",
                    post.getId(), actorAccountHolderId, ex);
        }
    }

    public void publishOfficialPostPublished(PrayerPost post) {
        try {
            List<UUID> audience = resolveAudience(post);
            if (audience.isEmpty()) {
                log.info("Prayer official publish notification skipped postId={} visibility={} reason=no-audience",
                        post.getId(), post.getVisibility());
                return;
            }

            NotificationDeepLinkRequest deepLink = buildOfficialDeepLink(post);
            for (UUID recipientId : audience) {
                publishNotification(
                        recipientId,
                        NotificationType.PRAYER_OFFICIAL_CONTENT_PUBLISHED,
                        "PRAYER_OFFICIAL_CONTENT_PUBLISHED",
                        Map.of("title", normalizeTitle(post.getTitle())),
                        post.getId().toString(),
                        deepLink
                );
            }
            log.info("Prayer official publish notifications queued postId={} recipients={}", post.getId(), audience.size());
        } catch (RuntimeException ex) {
            log.warn("Prayer official publish notification failed postId={}", post.getId(), ex);
        }
    }

    public void publishDailyPrayerAvailable(UUID accountHolderId, PrayerDailyContent dailyPrayer) {
        try {
            publishNotification(
                    accountHolderId,
                    NotificationType.PRAYER_REMINDER,
                    "PRAYER_DAILY_AVAILABLE",
                    Map.of("title", normalizeTitle(dailyPrayer.getTitle())),
                    dailyPrayer.getId().toString(),
                    buildDailyDeepLink(dailyPrayer.getId(), "Prier maintenant")
            );
        } catch (RuntimeException ex) {
            log.warn("Prayer daily available notification failed accountHolderId={} dailyPrayerId={}",
                    accountHolderId, dailyPrayer.getId(), ex);
        }
    }

    public void publishStreakAtRisk(UUID accountHolderId, PrayerDailyContent dailyPrayer, int currentStreak) {
        try {
            publishNotification(
                    accountHolderId,
                    NotificationType.PRAYER_STREAK_AT_RISK,
                    "PRAYER_STREAK_AT_RISK",
                    Map.of(
                            "title", normalizeTitle(dailyPrayer.getTitle()),
                            "streakCount", Integer.toString(currentStreak)
                    ),
                    dailyPrayer.getId().toString(),
                    buildDailyDeepLink(dailyPrayer.getId(), "Sauver ma serie")
            );
        } catch (RuntimeException ex) {
            log.warn("Prayer streak-at-risk notification failed accountHolderId={} dailyPrayerId={}",
                    accountHolderId, dailyPrayer.getId(), ex);
        }
    }

    private void publishNotification(UUID accountHolderId,
                                     NotificationType type,
                                     String templateCode,
                                     Map<String, String> variables,
                                     String targetId,
                                     NotificationDeepLinkRequest deepLink) {
        domainEventPublisher.publish(new NotificationRequestedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                accountHolderId,
                null,
                type,
                NotificationChannel.IN_APP,
                templateCode,
                null,
                variables,
                targetId,
                deepLink,
                true
        ));
    }

    private List<UUID> resolveAudience(PrayerPost post) {
        if (post.getVisibility() == PrayerVisibilityScope.GLOBAL) {
            return accountHolderRepository.findAllIds();
        }
        if (post.getVisibility() == PrayerVisibilityScope.COMMUNITY && post.getAudienceCountryCode() != null) {
            return accountHolderRepository.findAllIdsByServiceCountryCode(post.getAudienceCountryCode());
        }
        if (post.getVisibility() == PrayerVisibilityScope.CHURCH) {
            log.info("Prayer official publish notification unresolved postId={} reason=church-context-not-implemented", post.getId());
        }
        return List.of();
    }

    private NotificationDeepLinkRequest buildFeedDeepLink(UUID postId, String label) {
        return new NotificationDeepLinkRequest(
                PrayerDeepLinkRouteKey.PRAYER_FEED.name(),
                label,
                Map.of("postId", postId.toString())
        );
    }

    private NotificationDeepLinkRequest buildDailyDeepLink(UUID dailyPrayerId, String label) {
        return new NotificationDeepLinkRequest(
                PrayerDeepLinkRouteKey.PRAYER_DAY.name(),
                label,
                Map.of("dailyPrayerId", dailyPrayerId.toString())
        );
    }

    private NotificationDeepLinkRequest buildOfficialDeepLink(PrayerPost post) {
        if (post.getDeepLinkRouteKey() == null) {
            return buildFeedDeepLink(post.getId(), "Voir la publication");
        }
        return new NotificationDeepLinkRequest(
                post.getDeepLinkRouteKey().name(),
                post.getDeepLinkLabel(),
                parseParams(post.getDeepLinkParamsJson())
        );
    }

    private Map<String, String> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(paramsJson, STRING_MAP);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private String normalizeDisplayName(String actorDisplayName) {
        if (actorDisplayName == null || actorDisplayName.isBlank()) {
            return "Un membre";
        }
        return actorDisplayName.trim();
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Publication de priere";
        }
        return title.trim();
    }
}
