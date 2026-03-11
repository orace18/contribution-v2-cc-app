package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleContributionEvent;
import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CircleContributionActivityListener implements DomainEventListener<CircleContributionEvent> {

    private static final String LISTENER_KEY = "circlesocial.circle-contribution-activity";

    private final CircleActivityService circleActivityService;
    private final IdempotentEventListenerExecutor listenerExecutor;

    @Override
    @EventListener
    public void on(CircleContributionEvent event) {
        listenerExecutor.execute(LISTENER_KEY, event, () -> circleActivityService.logActivity(
                event.circleId(),
                event.contributorUserId(),
                ActivityType.CONTRIBUTION_PAID,
                event.accountHolderId(),
                "Contribution paid: " + event.amount() + " " + event.currency()
        ));
    }
}
