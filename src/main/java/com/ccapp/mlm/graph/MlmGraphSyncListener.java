package com.ccapp.mlm.graph;

import com.ccapp.common.events.DomainEventListener;
import com.ccapp.mlm.event.MlmPlacementCreatedEvent;
import com.ccapp.mobile.onboarding.event.MobileOnboardingEvent;
import com.ccapp.mobile.onboarding.event.MobileOnboardingEventType;
import com.ccapp.progress.event.AccountHolderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MlmGraphSyncListener implements DomainEventListener<AccountHolderCreatedEvent> {

    private final MlmGraphSyncService mlmGraphSyncService;

    @Override
    @EventListener
    public void on(AccountHolderCreatedEvent event) {
        try {
            mlmGraphSyncService.syncUserCreation(event.accountHolderId());
        } catch (RuntimeException ex) {
            log.warn("mlm_graph_sync_account_holder_created_failed accountHolderId={}", event.accountHolderId(), ex);
        }
    }

    @EventListener
    public void onPlacementCreated(MlmPlacementCreatedEvent event) {
        try {
            mlmGraphSyncService.syncPlacementAssignment(
                    event.accountHolderId(),
                    event.sponsorAccountHolderId(),
                    event.placedUnderAccountHolderId()
            );
        } catch (RuntimeException ex) {
            log.warn("mlm_graph_sync_placement_created_failed accountHolderId={}", event.accountHolderId(), ex);
        }
    }

    @EventListener
    public void onMobileOnboardingEvent(MobileOnboardingEvent event) {
        if (event.eventType() != MobileOnboardingEventType.NETWORK_LINKED) {
            return;
        }
        try {
            mlmGraphSyncService.syncUserFromSourceOfTruth(event.accountHolderId());
        } catch (RuntimeException ex) {
            log.warn("mlm_graph_sync_mobile_network_linked_failed accountHolderId={}", event.accountHolderId(), ex);
        }
    }
}

