package com.ccapp.prayer.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.NotificationDeepLinkRequest;
import com.ccapp.notifications.event.NotificationRequestedEvent;
import com.ccapp.prayer.domain.entity.PrayerPost;
import com.ccapp.prayer.domain.enums.PrayerDeepLinkRouteKey;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.domain.enums.PrayerPostType;
import com.ccapp.prayer.domain.enums.PrayerVisibilityScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrayerNotificationPublisherTest {

    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private AccountHolderRepository accountHolderRepository;

    private PrayerNotificationPublisher prayerNotificationPublisher;

    @BeforeEach
    void setUp() {
        prayerNotificationPublisher = new PrayerNotificationPublisher(
                domainEventPublisher,
                accountHolderRepository,
                new ObjectMapper()
        );
    }

    @Test
    void publishSupportReceived_shouldEmitExplicitPrayerNotificationInsteadOfLegacyCircleName() {
        UUID authorId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        PrayerPost post = PrayerPost.builder()
                .id(postId)
                .postType(PrayerPostType.REQUEST)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .authorAccountHolderId(authorId)
                .visibility(PrayerVisibilityScope.GLOBAL)
                .content("Priez pour mon activite.")
                .build();

        prayerNotificationPublisher.publishSupportReceived(actorAccountHolderId, "Sarah Doe", post);

        ArgumentCaptor<NotificationRequestedEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        NotificationRequestedEvent event = eventCaptor.getValue();

        assertEquals(authorId, event.accountHolderId());
        assertEquals(NotificationType.PRAYER_SUPPORT_RECEIVED, event.type());
        assertNotEquals(NotificationType.CIRCLE_PRAYED, event.type());
        assertEquals("PRAYER_SUPPORT_RECEIVED", event.templateCode());
        assertEquals(postId.toString(), event.targetId());
        assertNotNull(event.deepLink());
        assertEquals("PRAYER_FEED", event.deepLink().routeKey());
        assertEquals(postId.toString(), event.deepLink().params().get("postId"));
    }

    @Test
    void publishCommentReceived_shouldEmitCommentNotificationForPostAuthor() {
        UUID authorId = UUID.randomUUID();
        UUID actorAccountHolderId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        PrayerPost post = PrayerPost.builder()
                .id(postId)
                .postType(PrayerPostType.REQUEST)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .authorAccountHolderId(authorId)
                .visibility(PrayerVisibilityScope.GLOBAL)
                .content("Besoin d encouragement.")
                .build();

        prayerNotificationPublisher.publishCommentReceived(actorAccountHolderId, "Daniel Doe", post);

        ArgumentCaptor<NotificationRequestedEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertEquals(NotificationType.PRAYER_COMMENT_RECEIVED, eventCaptor.getValue().type());
        assertEquals("Daniel Doe", eventCaptor.getValue().variables().get("actorDisplayName"));
    }

    @Test
    void publishOfficialPostPublished_shouldPreserveStructuredDeepLinkPayload() {
        UUID recipientA = UUID.randomUUID();
        UUID recipientB = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(accountHolderRepository.findAllIdsByServiceCountryCode("BJ")).thenReturn(List.of(recipientA, recipientB));

        PrayerPost post = PrayerPost.builder()
                .id(postId)
                .postType(PrayerPostType.OFFICIAL)
                .lifecycleStatus(PrayerPostLifecycleStatus.PUBLISHED)
                .title("Nouveau contenu officiel")
                .content("Ouvre ton wallet avec foi.")
                .visibility(PrayerVisibilityScope.COMMUNITY)
                .audienceCountryCode("BJ")
                .deepLinkRouteKey(PrayerDeepLinkRouteKey.WALLET_HOME)
                .deepLinkLabel("Ouvrir mon wallet")
                .deepLinkParamsJson("{\"tab\":\"overview\"}")
                .build();

        prayerNotificationPublisher.publishOfficialPostPublished(post);

        ArgumentCaptor<NotificationRequestedEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(domainEventPublisher, times(2)).publish(eventCaptor.capture());
        List<NotificationRequestedEvent> events = eventCaptor.getAllValues();

        assertEquals(NotificationType.PRAYER_OFFICIAL_CONTENT_PUBLISHED, events.getFirst().type());
        assertEquals(postId.toString(), events.getFirst().targetId());
        NotificationDeepLinkRequest deepLink = events.getFirst().deepLink();
        assertNotNull(deepLink);
        assertEquals("WALLET_HOME", deepLink.routeKey());
        assertEquals("Ouvrir mon wallet", deepLink.label());
        assertEquals(Map.of("tab", "overview"), deepLink.params());
    }
}
