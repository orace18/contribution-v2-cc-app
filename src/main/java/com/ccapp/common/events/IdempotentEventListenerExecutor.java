package com.ccapp.common.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdempotentEventListenerExecutor {

    private final EventListenerCheckpointRepository checkpointRepository;

    @Transactional
    public void execute(String listenerKey, DomainEvent event, Runnable handler) {
        if (event == null || event.eventId() == null) {
            handler.run();
            return;
        }

        int inserted = checkpointRepository.insertIfAbsent(
                UUID.randomUUID(),
                listenerKey,
                event.eventId(),
                OffsetDateTime.now()
        );
        if (inserted == 0) {
            return;
        }

        handler.run();
    }
}
