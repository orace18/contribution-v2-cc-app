package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.domain.entity.CircleActivity;
import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.dto.CircleActivityResponse;
import com.ccapp.circlesocial.repository.CircleActivityRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.notifications.event.NotificationRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CircleActivityService.
 *
 * Key architectural invariant under test:
 *   circle_activity.actor_user_id stores an account_holder_id.
 *   Members may have no app_user record (no Android account).
 *   The service must never perform an app_user lookup on this value.
 */
@ExtendWith(MockitoExtension.class)
class CircleActivityServiceTest {

    private static final UUID CIRCLE_ID         = UUID.randomUUID();
    private static final UUID ACCOUNT_HOLDER_ID = UUID.randomUUID();

    @Mock private CircleActivityRepository circleActivityRepository;
    @Mock private CircleRepository          circleRepository;
    @Mock private CircleProgressService     circleProgressService;
    @Mock private CircleScoreService        circleScoreService;
    @Mock private DomainEventPublisher      domainEventPublisher;

    @InjectMocks
    private CircleActivityService service;

    // ── helpers ──────────────────────────────────────────────────────────────

    private CircleActivity savedActivity(UUID actorAccountHolderId) {
        return CircleActivity.builder()
                .id(UUID.randomUUID())
                .circleId(CIRCLE_ID)
                .actorUserId(actorAccountHolderId)   // column name legacy; value is account_holder_id
                .activityType(ActivityType.MEMBER_JOINED)
                .message("Member joined circle")
                .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    /**
     * An account_holder without any app_user / Android account must be able to
     * generate circle activity. The FK now points to account_holders(id), so as
     * long as the account_holder exists the insert must succeed — no app_user
     * lookup should block or alter the flow.
     */
    @Test
    void logActivity_accountHolderWithoutAppUser_shouldSucceedAndReturnResponse() {
        when(circleRepository.existsById(CIRCLE_ID)).thenReturn(true);
        CircleActivity activity = savedActivity(ACCOUNT_HOLDER_ID);
        when(circleActivityRepository.save(any(CircleActivity.class))).thenReturn(activity);

        CircleActivityResponse response = service.logActivity(
                CIRCLE_ID,
                ACCOUNT_HOLDER_ID,
                ActivityType.MEMBER_JOINED,
                null,
                "Member joined circle"
        );

        assertThat(response.circleId()).isEqualTo(CIRCLE_ID);
        assertThat(response.actorUserId()).isEqualTo(ACCOUNT_HOLDER_ID);
        assertThat(response.activityType()).isEqualTo(ActivityType.MEMBER_JOINED);
    }

    /**
     * The notification event must be emitted with the account_holder_id directly —
     * no intermediate app_user resolution. Members without Android must still
     * receive in-app notifications when they acquire one later.
     */
    @Test
    void logActivity_notification_usesAccountHolderIdDirectly() {
        when(circleRepository.existsById(CIRCLE_ID)).thenReturn(true);
        CircleActivity activity = savedActivity(ACCOUNT_HOLDER_ID);
        when(circleActivityRepository.save(any(CircleActivity.class))).thenReturn(activity);

        service.logActivity(CIRCLE_ID, ACCOUNT_HOLDER_ID, ActivityType.MEMBER_JOINED, null, "Member joined circle");

        ArgumentCaptor<NotificationRequestedEvent> captor =
                ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(domainEventPublisher).publish(captor.capture());

        NotificationRequestedEvent event = captor.getValue();
        // Both recipient and sender fields must be the account_holder_id.
        // A lookup through app_user would have returned a different or null value.
        assertThat(event.accountHolderId()).isEqualTo(ACCOUNT_HOLDER_ID);
    }

    /**
     * Verifies that the actor stored in circle_activity is exactly the
     * account_holder_id passed in — no transformation, no resolution through
     * a users or app_users table.
     */
    @Test
    void logActivity_persistsActorAsAccountHolderId() {
        when(circleRepository.existsById(CIRCLE_ID)).thenReturn(true);
        CircleActivity activity = savedActivity(ACCOUNT_HOLDER_ID);
        when(circleActivityRepository.save(any(CircleActivity.class))).thenReturn(activity);

        service.logActivity(CIRCLE_ID, ACCOUNT_HOLDER_ID, ActivityType.MEMBER_JOINED, null, "Member joined circle");

        ArgumentCaptor<CircleActivity> captor = ArgumentCaptor.forClass(CircleActivity.class);
        verify(circleActivityRepository).save(captor.capture());
        assertThat(captor.getValue().getActorUserId()).isEqualTo(ACCOUNT_HOLDER_ID);
    }
}
