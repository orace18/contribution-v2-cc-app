package com.ccapp.mlm.graph;

import com.ccapp.mlm.event.MlmPlacementCreatedEvent;
import com.ccapp.mobile.onboarding.event.MobileOnboardingEvent;
import com.ccapp.mobile.onboarding.event.MobileOnboardingEventType;
import com.ccapp.progress.event.AccountHolderCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MlmGraphSyncListenerTest {

    @Mock
    private MlmGraphSyncService mlmGraphSyncService;

    @InjectMocks
    private MlmGraphSyncListener mlmGraphSyncListener;

    @Test
    void accountHolderCreated_shouldSyncUserNode() {
        UUID accountHolderId = UUID.randomUUID();

        mlmGraphSyncListener.on(AccountHolderCreatedEvent.create(accountHolderId));

        verify(mlmGraphSyncService).syncUserCreation(accountHolderId);
    }

    @Test
    void placementCreated_shouldSyncSponsorAndPlacementLinks() {
        UUID accountHolderId = UUID.randomUUID();
        UUID sponsorId = UUID.randomUUID();
        UUID placementId = UUID.randomUUID();

        mlmGraphSyncListener.onPlacementCreated(
                MlmPlacementCreatedEvent.create(accountHolderId, sponsorId, placementId)
        );

        verify(mlmGraphSyncService).syncPlacementAssignment(accountHolderId, sponsorId, placementId);
    }

    @Test
    void freeOrPaidNetworkLinkedEvent_shouldSyncFromSourceOfTruth() {
        UUID accountHolderId = UUID.randomUUID();
        MobileOnboardingEvent event = MobileOnboardingEvent.create(
                accountHolderId,
                MobileOnboardingEventType.NETWORK_LINKED,
                Map.of("flow", "FREE_OR_PAID")
        );

        mlmGraphSyncListener.onMobileOnboardingEvent(event);

        verify(mlmGraphSyncService).syncUserFromSourceOfTruth(accountHolderId);
    }

    @Test
    void nonNetworkEvent_shouldNotTriggerGraphSync() {
        MobileOnboardingEvent event = MobileOnboardingEvent.create(
                UUID.randomUUID(),
                MobileOnboardingEventType.PLAN_SELECTED,
                Map.of()
        );

        mlmGraphSyncListener.onMobileOnboardingEvent(event);

        verify(mlmGraphSyncService, never()).syncUserFromSourceOfTruth(event.accountHolderId());
    }
}

