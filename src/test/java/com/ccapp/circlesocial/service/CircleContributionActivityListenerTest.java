package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleContributionEvent;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CircleContributionActivityListenerTest {

    @Mock
    private CircleActivityService circleActivityService;
    @Mock
    private IdempotentEventListenerExecutor listenerExecutor;

    @InjectMocks
    private CircleContributionActivityListener listener;

    @Test
    void on_shouldLogContributionActivity() {
        CircleContributionEvent event = new CircleContributionEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("3000"),
                "XOF"
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());

        listener.on(event);

        verify(circleActivityService).logActivity(
                event.circleId(),
                event.contributorUserId(),
                ActivityType.CONTRIBUTION_PAID,
                event.accountHolderId(),
                "Contribution paid: " + event.amount() + " " + event.currency()
        );
    }
}
