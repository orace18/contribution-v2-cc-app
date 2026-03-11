package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.common.events.DomainEventListener;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.credit.event.LoanRepaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanRepaidCircleActivityListener implements DomainEventListener<LoanRepaidEvent> {

    private static final String LISTENER_KEY = "circlesocial.loan-repaid-activity";

    private final CircleActivityService circleActivityService;
    private final IdempotentEventListenerExecutor listenerExecutor;

    @Override
    @EventListener
    public void on(LoanRepaidEvent event) {
        listenerExecutor.execute(LISTENER_KEY, event, () -> circleActivityService.logActivity(
                event.circleId(),
                event.actorUserId(),
                ActivityType.LOAN_REPAID,
                event.loanId(),
                "Loan fully repaid"
        ));
    }
}
