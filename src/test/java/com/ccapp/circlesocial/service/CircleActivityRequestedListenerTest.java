package com.ccapp.circlesocial.service;

import com.ccapp.circlesocial.domain.enums.ActivityType;
import com.ccapp.circlesocial.event.CircleActivityRequestedEvent;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CircleActivityRequestedListenerTest {

    @Mock
    private CircleActivityService circleActivityService;
    @Mock
    private IdempotentEventListenerExecutor listenerExecutor;

    @InjectMocks
    private CircleActivityRequestedListener listener;

    @Test
    void on_shouldDelegateToCircleActivityService() {
        CircleActivityRequestedEvent event = new CircleActivityRequestedEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ActivityType.MEMBER_JOINED,
                UUID.randomUUID(),
                "Member joined circle"
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());

        listener.on(event);

        verify(circleActivityService).logActivity(
                event.circleId(),
                event.actorUserId(),
                event.activityType(),
                event.referenceId(),
                event.message()
        );
    }
}
