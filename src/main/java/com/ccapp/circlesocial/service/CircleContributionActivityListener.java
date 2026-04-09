package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.event.CircleContributionEvent;
import com.ccapp.common.events.DomainEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Deprecated(since = "2026-04", forRemoval = false)
@Slf4j
public class CircleContributionActivityListener implements DomainEventListener<CircleContributionEvent> {

    private static final String LISTENER_KEY = "circlesocial.circle-contribution-activity";

    @Override
    @EventListener
    public void on(CircleContributionEvent event) {
        log.warn("legacy_circle_contribution_event_ignored listenerKey={} circleId={} accountHolderId={}",
                LISTENER_KEY,
                event.circleId(),
                event.accountHolderId());
    }
}
